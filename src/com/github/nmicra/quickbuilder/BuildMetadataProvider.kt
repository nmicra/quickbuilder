package com.github.nmicra.quickbuilder

import com.google.common.collect.ArrayListMultimap
import org.jgrapht.GraphPath
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.traverse.BreadthFirstIterator
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.measureTimeMillis


object BuildMetadataProvider {

    private val logger = LoggerFactory.getLogger("com.github.nmicra.quickbuilder.BuildMetadataProvider")


    /**
     * mapping between codebaseName (like development,master), to whole Metadata collected for that particular codebase
     */
    val codebase2MetadataMap = mutableMapOf<String, BuildMetadata>()

    /**
     * mapping codebaseName (like development,master) to actual location of the codebase in the filesystem(eg: /home/branches/development)
     */
    val codeBaseMap by lazy {val tempStr = appEnv.config.propertyOrNull("codebasemapping")?.getString()
                                ?: throw error("Please specify the codebasemapping through commandline! Example: -P:codebasemapping=development=/home/branches/development,master=/home/branches/master")
                                tempStr.split(",").associate {
                                    val (left, right) = it.split("=")
                                    left to right
                                }  }

    /**
     * mapping between codebaseName (like development,master), and different flags used for that codebase
     */
    val codebase2FlagsMap by lazy {
        codeBaseMap.keys.associateWith { BuildHelperFlags() }
    }

    /**
     * Just a caching Map to improve performance
     */
    val artifactsToBuildCache = mutableMapOf<String,Set<String>>()



    /**
     * Searches for all poms in the codebase [NOTE: hardcoded depth=6]
     * @return set of absolutePath for the poms that were found.
     */
    private fun loadPomXmls(codeBase : String) : Set<String> = File(codeBase).walkTopDown().maxDepth(6).asSequence()
        .filter { it.isFile }
        .filter { it.name=="pom.xml" }
        .map { it.absolutePath.removeSuffix("pom.xml") }
        .map { it.removePrefix(File(codeBase).absolutePath) }
        .map { it.replace('\\','/') } // relevant only in windows machines
        .map { it.removePrefix("/") }
        .sortedByDescending { it.length }.toSet()


    fun generateMetadata(codebaseName: String, codebase : String? = null) : BuildMetadata {
        lateinit var metadata : BuildMetadata
        artifactsToBuildCache.clear()
        val duration = measureTimeMillis {
            val codeLocation = codebase ?: codeBaseMap[codebaseName]!!
            val xmls = loadPomXmls(codeLocation).filterNot { it.contains("node_modules") }
                .filterNot { it.contains("devenv/pompoms") }.toSet() // Exclude directories
            metadata = BuildMetadata(xmls)

            xmls.forEach {
                val groupArtifact = XmlUtil.groupArtifactIdForPomFile(File("$codeLocation/${it}pom.xml"))
                    ?: error("couldn't extract group:artifact for pom: $it [codebaseName=$codebaseName, codeLocation=$codeLocation]")
                metadata.inverseDependencyGraph.addVertex(groupArtifact)
                metadata.groupArtifact2ModuleMap[groupArtifact] = it.removeSuffix("/")
                val dependencies = XmlUtil.extractDependencies(File("$codeLocation/$it/pom.xml").readText())
                    .filter { it.groupId.startsWith("github.nmicra") }
                    .filter { it.scope == "compile" }
                dependencies.forEach {
                    metadata.inverseDependencyGraph.addVertex("${it.groupId}:${it.artifactId}")
                    metadata.inverseDependencyGraph.addEdge("${it.groupId}:${it.artifactId}", groupArtifact)
                }
            }
            getManualDependencyMapping().asMap().forEach{ mapEntry ->
                mapEntry.value.forEach {

                    metadata.inverseDependencyGraph.addEdge(mapEntry.key,it)
                }
            }
        }
        logger.info("metadata generation [codebaseName=$codebaseName] took $duration milis")
        return metadata
    }



    fun artifactsToBuildForGivenChangedArtifact(codebaseName: String, artifact : String) : Set<String>{

        fun helper(codebaseName: String, artifact : String) : Set<String> {
            val graph = codebase2MetadataMap[codebaseName]!!.inverseDependencyGraph
            if (graph.containsVertex(artifact).not()) throw error("Requested artifact: $artifact wasn't found in the graph")
            val set = mutableSetOf(artifact)
            val iter = BreadthFirstIterator(graph, artifact)
            while (iter.hasNext()) {
                set.add(iter.next())
            }
            return set
        }
        return artifactsToBuildCache.getOrPut("$codebaseName-$artifact") { helper(codebaseName,artifact)}
    }

    fun getPathFromDependencyGraph(codebaseName: String, src : String, dest : String): GraphPath<String, DefaultEdge>? {
        val graph = codebase2MetadataMap[codebaseName]!!.inverseDependencyGraph
        val dijkstraAlg = DijkstraShortestPath(graph)
        return dijkstraAlg.getPath(src,dest)
    }

    fun getDependencyPathTo(codebaseName: String, dest : String): Set<String> {
        val graph = codebase2MetadataMap[codebaseName]!!.inverseDependencyGraph
        return graph.vertexSet().mapNotNull {DijkstraShortestPath.findPathBetween(graph,it,dest)}.map { it.toString() }.toSet()
    }

    fun getDependencyPathFrom(codebaseName: String, src : String): Set<String> {
        val graph = codebase2MetadataMap[codebaseName]!!.inverseDependencyGraph
        val set = mutableSetOf<String>()
        val iter = BreadthFirstIterator(graph, src)
        while (iter.hasNext()) {
            set.add(iter.next())
        }
        return set
    }


    fun getManualDependencyMapping() : ArrayListMultimap<String, String> {
        val map : ArrayListMultimap<String,String> = ArrayListMultimap.create()
        val list = this::class.java.classLoader.getResource("manual-dependency-map.conf").readText().split("\n").toList()
        list.forEach { val (left, right) = it.split("=>")
            map.put(left.trim(),right.trim())}
        return map
    }

}