package com.disparko.quickbuilder

import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Used to keep history, tracks pom generation. Represents single build
 */
data class SingleBuild(val buildNr : Int, val changes: String, val generatedPom : String, val codebaseName : String, var buildStatus : String = "NOT DETERMINED YET")

data class Dependency(val groupId : String, val artifactId : String, val scope : String = "compile")

data class BuildHelperFlags(

    /**
     * when TRUE, flag indicates that, svn update in progress     */
    val svnUpInProgressFlag : AtomicBoolean = AtomicBoolean(false),
    /**
     * when TRUE, flag indicates that metadata is currently regenerated
     */
    val rebuildMetadataInProgressFlag : AtomicBoolean = AtomicBoolean(false)
   )


data class BuildMetadata(
    /**
     * filesystem path to pom.xml(s) */
    val pomXmls: Set<String>,
    /**
     * graph which represents links between all dependencies */
    val inverseDependencyGraph : DefaultDirectedGraph<String, DefaultEdge> = DefaultDirectedGraph(DefaultEdge::class.java),
    /**
     * mapping between pom groupId:artifactId string and the module path in the filesystem   */
    val groupArtifact2ModuleMap: MutableMap<String, String> = mutableMapOf())
