package com.disparko.quickbuilder

import com.google.common.collect.EvictingQueue
import io.github.rybalkinsd.kohttp.ext.httpGet
import io.ktor.application.Application
import io.ktor.application.ApplicationEnvironment
import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.error
import kotlinx.html.*
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

private val infoForUser = """
    Usage: java -jar quickbuilder-1.0-SNAPSHOT-jar-with-dependencies.jar -port=8181 -P:codebasemapping=development=/home/branches/development,master=/home/branches/master
    NOTE: default port is ==> 8080 (if not specified)
    """.trimIndent()

private val buildTemplatePomXml = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<name>This pom was generated for incremental build</name>
	<groupId>com.disparko.poms</groupId>
	<artifactId>incremental-refactor</artifactId>
	<packaging>pom</packaging>
	<version>tmp</version>
	
	<modules>
        <module>devenv/setup</module>
        MODULES_PLACEHOLDER
	</modules>
</project>""".trimIndent()


lateinit var appEnv: ApplicationEnvironment


private val previousBuildModules by lazy {
    BuildMetadataProvider.codeBaseMap.keys.associateWith { mutableSetOf<String>() }
}

private val logger = LoggerFactory.getLogger("com.disparko.quickbuilder")

private val historyList = EvictingQueue.create<SingleBuild>(50)

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.appStructure() {
    logger.info(infoForUser)
    appEnv = environment

    BuildMetadataProvider.codeBaseMap.keys.forEach {
        BuildMetadataProvider.codebase2MetadataMap[it] = BuildMetadataProvider.generateMetadata(it)
    }


    routing {

        static("/static"){
            resources("static")
        }

        get("/") {
            call.respondHtml {
                head {
                    styleLink("/static/lib/bootstrap.min.css")
                    styleLink("/static/lib/bootstrap-vue.css")
                }
                body {
                    div {
                        attributes["class"] = "alert alert-primary"
                        +"Take me to the "
                        a {
                            attributes["class"]="alert-link"
                            attributes["href"]="/static/actions.html"
                            +"Actions Page"
                        }
                    }
                    h1 { +"History of generated poms" }
                    table {
                        attributes["border"] = "1"
                        tr {
                            th { +"BuildNr" }
                            th { +"Changes" }
                            th { +"Generated Pom" }
                            th { +"Build Status" }
                        }
                        singlebuildrows(historyList.sortedByDescending { it.buildNr })
                    }
                }
            }
        }

        get("/test/{codebaseName}"){
            when {
                call.parameters["codebaseName"].isNullOrEmpty() -> call.respondText("You must specify the codebase (eg. development, master)")
                call.request.headers["changes"].isNullOrEmpty() -> call.respondText("You must specify comma separated AFFECTED files in Header \"changes\"")
                BuildMetadataProvider.codeBaseMap.keys.contains(call.parameters["codebaseName"]!!.toLowerCase()).not() -> call.respondText("The codebase name is not valid. Valid values are: ${BuildMetadataProvider.codeBaseMap.keys}")
            }
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            val changes =  call.request.headers["changes"]?.replace('\\','/')?.split(",")!!.toSet()
            val set = modulesToBuildByChanges(changes, codebaseName, "SUCCESS").filterNot { it.startsWith("devenv/setup") }.toSet()
            val finalModulesStr = removeSubModulesWhenParentExist(set).toList().sorted().joinToString("\n") { "<module>$it</module>" }
            call.respondText(buildTemplatePomXml.replace("MODULES_PLACEHOLDER", finalModulesStr))
        }

        get("/allmodules/{codebaseName}"){
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            call.respondText(BuildMetadataProvider.codebase2MetadataMap[codebaseName]!!.groupArtifact2ModuleMap.keys.sorted().joinToString(","))
        }

        get("/rebuildmetadata/{codebaseName}"){
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            BuildMetadataProvider.codebase2MetadataMap[codebaseName] = BuildMetadataProvider.generateMetadata(codebaseName)
            call.respondText("rebuildmetadata Done")
        }

        get("/path/{codebaseName}/{src}/{dest}"){
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            val src = call.parameters["src"]!!
            val dest = call.parameters["dest"]!!
            call.respondText( BuildMetadataProvider.getPathFromDependencyGraph(codebaseName,src,dest).toString())
        }

        get("/pathto/{codebaseName}/{dest}"){
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            val dest = call.parameters["dest"]!!
            call.respondText( BuildMetadataProvider.getDependencyPathTo(codebaseName,dest).joinToString("\n"))
        }

        get("/pathfrom/{codebaseName}/{src}"){
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            val src = call.parameters["src"]!!
            call.respondText( BuildMetadataProvider.getDependencyPathFrom(codebaseName,src).joinToString("\n"))
        }


        get("/pom/{codebaseName}/{buildNr}") {
            val codebaseName = call.parameters["codebaseName"]!!.toLowerCase()
            val buildNr = call.parameters["buildNr"]!!.toInt()
            val jenkinsUrlCodebase = when(codebaseName){
                "development" -> "Development"
                "master" -> "Master"
                else -> throw error("codebase $codebaseName not supported yet!")
            }

            logger.debug(">> pom requested for codebaseName=$codebaseName, buildNr=$buildNr")

            val previousBuildResult: Response =
                "http://myjenkins:8080/job/Builds/job/$jenkinsUrlCodebase/${buildNr.minus(1)}/api/xml?wrapper=changes&xpath=//result".httpGet()
            val currentBuildChanges: Response =
                "http://myjenkins:8080/job/Builds/job/$jenkinsUrlCodebase/$buildNr/api/xml?wrapper=changes&xpath=//changeSet//affectedPath".httpGet()
            val changedFilesXml = currentBuildChanges.body()!!.string().trim()


            while (BuildMetadataProvider.codebase2FlagsMap[codebaseName]?.rebuildMetadataInProgressFlag?.get()!!){
                logger.info(">> rebuildMetadataInProgressFlag=TRUE , waiting till it finish")
                Thread.sleep(500)
            }

            if (changedFilesXml.contains("pom.xml")){
                logger.debug(">> [POM.XML CHANGED] codebaseName=$codebaseName, buildNr=$buildNr")
                BuildMetadataProvider.codebase2MetadataMap[codebaseName] = BuildMetadataProvider.generateMetadata(codebaseName)
            }


            val previousBuildStatus = XmlUtil.extractBuildResultl(previousBuildResult.body()!!.string())
            val changes = XmlUtil.extractChangedFiles(changedFilesXml)
            logger.info(">> changes: $changes")
            historyList.firstOrNull { it.codebaseName == codebaseName && it.buildNr == buildNr.minus(1)}?.buildStatus  = previousBuildStatus



            val finalModulesStr =
            when {
                historyList.filter { it.codebaseName == codebaseName } // when last 5 builds are not "SUCCESS", then build entire CODEBASE
                    .filter { it.buildNr in buildNr.minus(1) downTo buildNr.minus(5) }.none { it.buildStatus == "SUCCESS" } -> allModulesAllArtifacts(codebaseName)
                changes.isEmpty() -> historyList.filter { it.codebaseName == codebaseName }.firstOrNull { it.buildNr == buildNr.minus(1) }?.generatedPom ?: allModulesAllArtifacts(codebaseName)
                else -> {
                    val set = modulesToBuildByChanges(changes, codebaseName, previousBuildStatus).filterNot { it.startsWith("devenv/setup") }.toSet() // devenv/setup is build anyway
                    removeSubModulesWhenParentExist(set).toList().sorted().joinToString("\n") { "<module>$it</module>" }
//                    modulesToBuildByChanges(changes, codebaseName, previousBuildStatus).filterNot { it.startsWith("devenv/setup") }.distinct().sorted().joinToString("\n") { "<module>$it</module>" }
                }
            }

            logger.info(">> modules generated for codebaseName=$codebaseName, buildNr=$buildNr is: $finalModulesStr")
            historyList.add(SingleBuild(buildNr, changes.toString(), finalModulesStr, codebaseName))
            call.respondText(buildTemplatePomXml.replace("MODULES_PLACEHOLDER", finalModulesStr))
        }
    }
}

private fun modulesToBuildByChanges(changes: Set<String>, codebaseName: String, previousBuildStatus: String): Set<String> {
    val affectedPoms = changes.map { changedFile ->
        BuildMetadataProvider.codebase2MetadataMap[codebaseName]!!.pomXmls.firstOrNull { pom ->
            changedFile.startsWith(
                pom
            )
        }
    }.filterNotNull().toSet()
    logger.debug(">> affectedPoms => $affectedPoms")

    var affectedModules =
        affectedPoms.map { File("${BuildMetadataProvider.codeBaseMap[codebaseName]}/${it}pom.xml") }
            .mapNotNull { XmlUtil.groupArtifactIdForPomFile(it) }
            .map { BuildMetadataProvider.artifactsToBuildForGivenChangedArtifact(codebaseName, it) }
            .flatMap { it.toMutableSet() }
    if (previousBuildStatus != "SUCCESS") {
        logger.debug(">> previousBuild is not SUCCESS adding previous modules => $previousBuildModules[codebaseName]")
        affectedModules += previousBuildModules[codebaseName]!!
    }
    previousBuildModules[codebaseName]?.clear()
    previousBuildModules[codebaseName]?.addAll(affectedModules)
    logger.debug(">> affectedModules => $affectedModules")

    return affectedModules.mapNotNull { BuildMetadataProvider.codebase2MetadataMap[codebaseName]!!.groupArtifact2ModuleMap[it] }
            .toSet()

}


private fun TABLE.singlebuildrows(lst: List<SingleBuild>) {
    for (build in lst) {
        tr {
            td { +build.buildNr.toString() }
            td { +build.changes }
            td { +build.generatedPom }
            td { +build.buildStatus }
            td { +build.codebaseName }
        }
    }
}

private val devenvArtifactsHardcoeded = """
        <module>devenv/build/deployables/legacy-web.war</module>
        <module>devenv/build/deployables/legacy-mobile.war</module>
    """.trimIndent()


/**
 * When you want to build entire codebase, use this fun to retrieve all modules and all final artifacts
 * that must be compiled
 */
fun allModulesAllArtifacts(codebaseName : String) : String = File("${BuildMetadataProvider.codeBaseMap[codebaseName]}/devenv/pompoms/refactor/pom.xml")
    .bufferedReader().readLines()
    .filter { it.contains("<module>") }
    .filterNot { it.contains("devenv/setup") } //because it was already specified in template pom (removed as duplicated)
    .filterNot { it.trim().startsWith("<!--") }
    .map { it.replace("../../../","") }
    .joinToString ("\n", postfix = "\n$devenvArtifactsHardcoeded")




/**
 * This fun removes all child modules, when parent module exists
 */
fun removeSubModulesWhenParentExist(modules: Set<String>): Set<String> = modules.filter {!isPrefixOfAnyItemInList(it, modules.toList().minus(it))}.toSet()

/**
 * Returns TRUE when the given item(String) is prefix of any other item in the given list
 */
fun isPrefixOfAnyItemInList(item: String, list: List<String>): Boolean = list.any { item.startsWith(it) }

/**
 * workingDir - from which directory the command should run
 * @return - produced text from stdout.
 */
fun String.runCommand(workingDir: File): String? {
    return try {
        val parts = this.split("\\s".toRegex())
        val proc = ProcessBuilder(parts)
            .directory(workingDir)
            .start()
        proc.waitFor(60, TimeUnit.MINUTES)
        proc.inputStream.bufferedReader().use { it.readText()} + "\n" +  proc.errorStream.bufferedReader().use { it.readText()}
    } catch(t: Throwable) {
        logger.error(t)
        t.stackTrace.toString()
    }
}

fun svnUp(codebaseName : String) : String {
    var svnOutput = "svnup in progress flag is TRUE. skipping ..."
    if (BuildMetadataProvider.codebase2FlagsMap[codebaseName]?.svnUpInProgressFlag?.getAndSet(true)?.not()!!){
        svnOutput = "svn up".runCommand(File(BuildMetadataProvider.codeBaseMap[codebaseName]))!!
        logger.debug("svn up >> $svnOutput")
        BuildMetadataProvider.codebase2FlagsMap[codebaseName]?.rebuildMetadataInProgressFlag?.set(false)
    }
    return svnOutput
}
