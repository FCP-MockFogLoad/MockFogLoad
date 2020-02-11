package com.example

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
data class App(val name: String, val cpu: Float? = null, val memory: String? = null)
@Serializable
data class Interface(val id: String, val active: Boolean? = null, val bandwidth: String? = null, val delay: String? = null, val loss: String? = null)
@Serializable
data class Generator(val id: String, val kind: String? =null, var endpoint: String? =null, var endpoint_port: String? =null, var active: Boolean? =null, var frequency: Int? = null, var protocol: String? =null, var format_string: String? =null, var seed: String? =null, var events_per_second: Int? =null)
@Serializable
data class Node(val id: String, val applications: List<App> = emptyList(), val interfaces: List<Interface> = emptyList(), val generators: List<Generator> = emptyList())
@Serializable
data class Stage(val id: String, val time: Long = 0, val node: List<Node> = emptyList())
@Serializable
data class Test(val testName: String? =null, val stages: List<Stage> = emptyList())
@Serializable
data class InstructionsG(val type: String, val timestamp: String, var data: Generator)
@Serializable
data class InstructionsA(val id: String, val timestamp: String, var data: App)
@Serializable
data class InstructionsI(val id: String, val timestamp: String, var data: Interface)
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    var orchestrator = Orchestrator()
    orchestrator.start()
    //orchestrator.getReport("null",3)
}

class Orchestrator() {
    // Delay before running the test script
    private val delay = 0
    // Agent port
    private val agentPort = "20200"
    // Generator port
    private val generatorPort = "20201"
    // Parse the test script
    private val test = parseTests("test.yaml")
    // Parse the node map
    private val nodeMap = parseNodes("map.yaml")
    // Get start time for the tests
    private val startTime = (System.currentTimeMillis() / 1000L) + delay

    // Client that will be used for sending HTTP requests to Agent and Generators
    val client = HttpClient(Apache) {
    }

    fun start() {
        var totalTime: Long = 0
        print("Running test: " + test.testName + ", starting at " + startTime + "\n")
        // Iterate over all test stages
        for (stage in test.stages){
            totalTime += stage.time
            // Temporary output of current test stage
            Timer("Stages", false).schedule((startTime+totalTime-(System.currentTimeMillis() / 1000L))*1000) {
                print("Running stage " + stage.id + "\n" + "Current time: " + (System.currentTimeMillis() / 1000L) + "\n")
                for (remote in nodeMap.nodes){
//                    val report = getReport(remote.ip, stage.id)
//                    print(report)
                }
            }
            // Iterate over all nodes in current stage
            for (node in stage.node){
                if (node.id == "all"){
                    // Iterate over all nodes in nodeMap
                    for (remote in this.nodeMap.nodes){
                        processInstructions(stage.id,  ((startTime + totalTime)*1000L), node, remote.ip)
                    }
                } else {
                    processInstructions(stage.id, ((startTime + totalTime)*1000L), node, getIPofNode(node.id))
                }
            }
        }
        Timer("Reports", false).schedule((startTime+totalTime+10-(System.currentTimeMillis() / 1000L))*1000) {
            //processReports()
        }
    }

    // Parses test yaml
    private fun parseTests(path: String): Test {
        val parser = ObjectMapper(YAMLFactory())
        parser.registerModule(KotlinModule())
        // Read file from path
        val yaml = Files.newBufferedReader(FileSystems.getDefault().getPath(path))
        val objTest = parser.readValue(yaml, Test::class.java)
        return(objTest)
    }

    // Parses nodeMap yaml
    private fun parseNodes(path: String): NodeMap {
        val parser = ObjectMapper(YAMLFactory())
        parser.registerModule(KotlinModule())
        // Read file from path
        val yaml = Files.newBufferedReader(FileSystems.getDefault().getPath(path))
        val objMap = parser.readValue(yaml, NodeMap::class.java)
        return(objMap)
    }

    // Packs and sends application related data to agent
    private fun packAndSendApplication(stage: String, timestamp: Long, app: App, host: String) {
        val instructions =
            InstructionsA(id = stage, timestamp = timestamp.toString(), data = app)

        val mapper = ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        val packedInstructions = "["+mapper.writeValueAsString(instructions)+"]"
        print(packedInstructions + "\n")
        sendInstructions(packedInstructions, this.client, "http://$host:${this.agentPort}/application")
    }

    // Packs and sends interface related instructions to agent
    private fun packAndSendInterface(stage: String, timestamp: Long, iface: Interface, host: String) {
        val instructions =
            InstructionsI(id = stage, timestamp = timestamp.toString(), data = iface)

        val mapper = ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        val packedInstructions = "["+mapper.writeValueAsString(instructions)+"]"
        print(packedInstructions + "\n")
        sendInstructions(packedInstructions, this.client, "http://$host:${this.agentPort}/interface")
    }

    // Packs and sends instructions to generator
    private fun packAndSendGenerator(generator: Generator, timestamp: Long, host: String) {
        val instructions =
            InstructionsG(type = "modify", timestamp = timestamp.toString(), data = generator)

        val mapper = ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        val packedInstructions = "["+mapper.writeValueAsString(instructions)+"]"
        print(packedInstructions + "\n")
        sendInstructions(packedInstructions, this.client, "http://$host:${this.generatorPort}/config")
    }

    // Sends Instructions to a remote
    private fun sendInstructions(instructions: String, client: HttpClient, host: String){
        print("Sending to $host \n")
        GlobalScope.launch (){
            client.call(host) {
                method = HttpMethod.Post
                body = TextContent(instructions, contentType = ContentType.Application.Json)
            }
        }
    }

    // Tries to find the IP for a given node ID in the NodeMap
    private fun getIPofNode(id: String?): String{
        for (node in this.nodeMap.nodes) {
            if (node.id == id) {
                return(node.ip)
            }
        }
        print("Node does not exist in map")
        exitProcess(0)
    }

    // Process and sends all instructions to agents and generators
    private fun processInstructions(stage: String, timestamp: Long, node: Node, host: String) {

        // Iterate over all applications
        for (app in node.applications) {
            packAndSendApplication(stage, timestamp, app, host)
        }
        // Iterate over all Interfaces
        for (iface in node.interfaces) {
            print("Packing iface")
            packAndSendInterface(stage, timestamp, iface,  host)
        }
        // Iterate over all generators
        for (generator in node.generators) {
            if (generator.endpoint != null){
                val remote = getIPofNode(generator.endpoint)
                if (generator.protocol == "HTTP" || generator.protocol == null){
                    generator.endpoint = "http://$remote:${generator.endpoint_port}/"
                } else {
                    generator.endpoint = "$remote:${generator.endpoint_port}"
                }
                generator.endpoint_port = null
            }
            packAndSendGenerator(generator, timestamp, host)
        }
    }

    private fun processReports() {
        for (node in nodeMap.nodes){
            var current_stage = 0
            for (stage in test.stages){
                print(getReport(node.ip, current_stage))
                current_stage++
            }
            print(getReport(node.ip, current_stage))
        }
    }

    fun getReport(agentIP: String, stage: Int): String{
        val host = "$agentIP:${this.agentPort}/reports/$stage"
        val text = runBlocking {
            return@runBlocking client.request<String> {
                url(host)
                method = HttpMethod.Get
            }
        }
        return text
    }
}

