package com.example

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)
@Serializable
data class NodeM(val id: String, val ip: String)
@Serializable
data class NodeMap(val name: String, val nodes: List<NodeM>)
@Serializable
data class Generator(val id: String, val frequency: Int = -1)
@Serializable
data class Interface(val id: String, val status: String)
@Serializable
data class Node(val id: String, val interfaces: List<Interface> = emptyList(), val generators: List<Generator> = emptyList())
@Serializable
data class Stage(val id: Int, val time: Long, val node: List<Node>)
@Serializable
data class Test(val testName: String, val stages: List<Stage>)
@Serializable
data class InstructionsG(val type: String, val timestamp: String, var data: Generator)
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val json = Json(JsonConfiguration.Stable)
    val test = parseTests(json)
    //val nodeMap = parseNodes()
    val startTime = System.currentTimeMillis() / 1000L
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
                for (generator in node.generators) {
                    val newInstructions =
                        InstructionsG(type = "modify", timestamp = (startTime + stage.time).toString(), data = generator)
                    val generatorInstruction = packGenerator(newInstructions, json)
                    print(generatorInstruction + "\n")
                    sendInstructions(arrayOf(newInstructions), client)
                }
            }
        }
    }
}

fun parseTests(json: Json): Test {
    val jsonTest = File("test.json").readText(Charsets.UTF_8)
    val objTest = json.parse(Test.serializer(), jsonTest)
    return(objTest)
}

fun parseNodes(json: Json): NodeMap {
    val jsonMap = File("map.json").readText(Charsets.UTF_8)
    val objMap = json.parse(NodeMap.serializer(), jsonMap)
    return(objMap)
}

fun packGenerator(instructions: InstructionsG, json: Json): String{
    val genJson = "["+json.stringify(InstructionsG.serializer(), instructions)+"]"

    return(genJson)
}

fun sendInstructions(instructions: Array<InstructionsG>, client: HttpClient){
    GlobalScope.launch (){
        //client.get
        client.post<String>{
            url("http://localhost:8080"+"/config")
            contentType(ContentType.Application.Json)
            body = instructions
        }
    }
}