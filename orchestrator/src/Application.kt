package com.example

import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Serializable
data class Generator(val id: String, val frequency: Int = -1, var time: Long = -1)
@Serializable
data class Interface(val id: String, val status: String)
@Serializable
data class Node(val id: String, val interfaces: List<Interface> = emptyList(), val generators: List<Generator> = emptyList())
@Serializable
data class Stage(val id: Int, val time: Long, val node: List<Node>)
@Serializable
data class Test(val testName: String, val stages: List<Stage>)
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val test = parseTests()
    val startTime = System.currentTimeMillis() / 1000L
    print("Running test: " + test.testName + ", starting at " + startTime + "\n")
    for (stage in test.stages){
        for (node in stage.node){
            for (generator in node.generators){
                generator.time = startTime + stage.time
                val generatorInstruction = packGenerator(generator)
                print(generatorInstruction)
            }
        }
    }
    val client = HttpClient(Apache) {
    }
    /*GlobalScope.launch (){
        //client.get
        client.post<Unit>{
            url("http://localhost:8080/config/temperature/region")
            body = "Bremen"

        }
    }*/
}

fun parseTests(): Test {
    val jsonTest = File("test.json").readText(Charsets.UTF_8)
    val json = Json(JsonConfiguration.Stable)
    val objTest = json.parse(Test.serializer(), jsonTest)
    return(objTest)
}

fun packGenerator(generator: Generator): String{
    val json = Json(JsonConfiguration.Stable)
    val genJson = json.stringify(Generator.serializer(), generator)
    return(genJson)
}