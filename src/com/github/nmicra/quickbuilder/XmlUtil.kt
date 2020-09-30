package com.github.nmicra.quickbuilder

import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

object XmlUtil {

    private val logger = LoggerFactory.getLogger("com.github.nmicra.quickbuilder.XmlUtil")
    private val dbFactory = DocumentBuilderFactory.newInstance()
    private val dBuilder  = dbFactory.newDocumentBuilder()
    private val xpFactory = XPathFactory.newInstance()
    private val xPath = xpFactory.newXPath()

    /**
     * Extracts the affected files from xml returned by Jenkins
     * @param changedFilesXml - xml that you get from Jenkins
     * @return Set of affected files
     */
    fun extractChangedFiles (changedFilesXml : String) : Set<String>{
        val xmlInput = InputSource(StringReader(changedFilesXml))
        val doc = dBuilder.parse(xmlInput)
        val qNodes = xPath.evaluate("/changes/affectedPath", doc, XPathConstants.NODESET) as NodeList
        val affectedFiles = HashSet<String>()
        for (i in 0 until qNodes.length) {
            affectedFiles.add(qNodes.item(i).textContent)
        }
        return affectedFiles
    }

    /**
     * Extracts groupId:ArtifactId for a given pom file.
     * @param - location of the pom file
     * @return String in format groupId:ArtifactId. May return NULL when extraction fails.
     */
    fun groupArtifactIdForPomFile (pom : File) : String?{
        logger.debug("[groupArtifactIdForPomXml] requested for pom: ${pom.absolutePath}")
        return try{
            val pomXml = pom.readText()
            val xmlInput = InputSource(StringReader(pomXml.trim().removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")))
            val doc = dBuilder.parse(xmlInput)
            var groupId = xPath.evaluate("/project/groupId", doc, XPathConstants.STRING) as String
            val artifactId = xPath.evaluate("/project/artifactId", doc, XPathConstants.STRING) as String
            if (groupId.isEmpty()) {
                groupId = xPath.evaluate("/project/parent/groupId", doc, XPathConstants.STRING) as String
            }
            "$groupId:$artifactId"
        } catch (ex : Exception){
            logger.warn("Failed to extract group:artifatct frpm pom: ${pom.absolutePath}\n $ex" )
            return null
        }

    }

    /**
     * Extracts the build execution result from xml returned by Jenkins
     * @param xml - xml that you get from Jenkins
     * @return The actual string. eg SUCCESS / FAILURE
     */
    fun extractBuildResultl (xml : String) : String = dBuilder.parse(InputSource(StringReader(xml))).let{ xPath.evaluate("/changes/result", it, XPathConstants.STRING) as String}


    fun extractDependencies (xml : String) : Set<Dependency> {
        logger.debug("[extractDependencies] requested for xml: $xml")
        val nodelist = dBuilder.parse(InputSource(StringReader(xml.trim().removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))))
            .let{ xPath.evaluate("/project/dependencies/dependency", it, XPathConstants.NODESET) as NodeList }

        val set = mutableSetOf<Dependency>()
        for (i in 0..nodelist.length) {

            if (nodelist.item(i)?.nodeType == Node.ELEMENT_NODE) {
                val item = nodelist.item(i) as Element
                val groupId = item.getElementsByTagName("groupId").item(0).textContent
                val artifactId = item.getElementsByTagName("artifactId").item(0).textContent
                val scope = item.getElementsByTagName("scope").item(0)?.textContent ?: "compile"
                set.add(Dependency(groupId, artifactId, scope))
            }
        }
        return set
    }

}

