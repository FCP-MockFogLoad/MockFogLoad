package com.example

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.features.json.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.system.exitProcess

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
@Serializable
data class NodeM(val id: String, val ip: String)
@Serializable
data class NodeMap(val nodes: List<NodeM>)
@Serializable
data class Generator(val id: String? =null, val kind: String? =null, var endpoint: String? =null, var active: Boolean? =null, var frequency: Int? = null)
@Serializable
data class Interface(val id: String, val status: String)
@Serializable
data class Node(val id: String, val interfaces: List<Interface> = emptyList(), val generators: List<Generator> = emptyList())
@Serializable
data class Stage(val id: Int? =null, val time: Long = 0, val node: List<Node> = emptyList())
@Serializable
data class Test(val testName: String? =null, val stages: List<Stage> = emptyList())
@Serializable
data class InstructionsG(val type: String, val timestamp: String, var data: Generator)
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val delay = 1
    val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val test = parseTests("test.yaml")
    val nodeMap = parseNodes("map.yaml")
    val startTime = (System.currentTimeMillis() / 1000L) + delay
    var totalTime: Long = 0
    print("Running test: " + test.testName + ", starting at " + startTime + "\n")
    for (stage in test.stages){
        totalTime += stage.time
        Timer("Tests", false).schedule((startTime+totalTime-(System.currentTimeMillis() / 1000L))*1000) {
            print("Running stage " + stage.id + "\n" + "Current time: " + (System.currentTimeMillis() / 1000L) + "\n")

        }
        for (node in stage.node){
            if (node.id == "all"){

            } else {
                val host = getIPofNode(nodeMap, node.id)
                for (generator in node.generators) {
                    if (generator.endpoint != null){
                        val remote = getIPofNode(nodeMap, generator.endpoint)
                        generator.endpoint = "http://$remote"
                    }
                    val newInstructions =
                        InstructionsG(type = "modify", timestamp = ((startTime + totalTime)*1000L).toString(), data = generator)
                    val generatorInstruction = packGenerator(newInstructions)
                    print(generatorInstruction + "\n")
                    sendInstructions(arrayOf(newInstructions), client, host)
                }
            }
        }
    }
}

fun parseTests(path: String): Test {
    val parser = ObjectMapper(YAMLFactory())
    parser.registerModule(KotlinModule())
    val yaml = Files.newBufferedReader(FileSystems.getDefault().getPath(path))
    val objTest = parser.readValue(yaml, Test::class.java)
    return(objTest)
}

fun parseNodes(path: String): NodeMap {
    val parser = ObjectMapper(YAMLFactory())
    parser.registerModule(KotlinModule())
    val yaml = Files.newBufferedReader(FileSystems.getDefault().getPath(path))
    val objMap = parser.readValue(yaml, NodeMap::class.java)
    return(objMap)
}

fun packGenerator(instructions: InstructionsG): String{
    val mapper = ObjectMapper();
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    val genJson = "["+mapper.writeValueAsString(instructions)+"]"

    return(genJson)
}

fun sendInstructions(instructions: Array<InstructionsG>, client: HttpClient, host: String){
    GlobalScope.launch (){
        client.post<String>{
            url("http://$host/config")
            contentType(ContentType.Application.Json)
            body = instructions
        }
    }
}

fun getIPofNode(map: NodeMap, id: String?): String{
    for (node in map.nodes) {
        if (node.id == id) {
            return(node.ip)
        }
    }
    print("Node does not exist in map")
    exitProcess(0)
}
