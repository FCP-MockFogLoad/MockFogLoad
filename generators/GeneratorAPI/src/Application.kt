package com.fcp

import com.amazonaws.services.s3.AmazonS3
import com.fcp.generators.BaseGenerator
import com.fcp.generators.IGeneratorValue
import com.fcp.generators.HeartRateGenerator
import com.fcp.generators.PowerGenerator
import com.fcp.generators.TaxiFaresGenerator
import com.fcp.generators.TaxiRidesGenerator
import com.fcp.generators.TemperatureGenerator
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mbed.coap.client.CoapClient
import com.mbed.coap.client.CoapClientBuilder
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.schedule
import kotlin.reflect.full.memberProperties
import kotlin.system.exitProcess


enum class GeneratorDataType {
    JSON,
    FormatString,
}

enum class GeneratorProtocol {
    HTTP,
    UDP,
    COAP,
}

class ActiveGenerator(val id: String, val config: ApplicationConfig, val generator: BaseGenerator) {
    /** The generator's output data type. */
    var dataType = GeneratorDataType.JSON

    /** The generator's format string (if dataType is FormatString). */
    var formatString = ""

    /** Generator frequency in milliseconds. */
    var frequency = 100L

    /** Whether or not the generator is currently active. */
    var active = false
        set(value) {
            if (active && !value) {
                field = false

                // Stop the timer task.
                timerTask!!.cancel()
                timerTask = null
            } else if (!active && value) {
                field = true

                println("starting ${generator.type} generator")
                reschedule()
            }
        }

    /** The granularity of the generator, i.e. the time in milliseconds
     * betweeen generated datapoints. */
    var granularity = 100L

    /** The protocol to use when sending data to the endpoint. */
    var protocol = GeneratorProtocol.HTTP
        set(value) {
            field = value
            udpIPAddress = null
            udpPort = null

            config.coapClient?.close()
            config.coapClient = null
        }

    /** The IP address of the UDP endpoint. */
    var udpIPAddress: InetAddress? = null

    /** The port of the UDP endpoint. */
    var udpPort: Int? = null

    /** The endpoint the generator should send to. */
    var endpoint = if (!isRunningInDockerContainer()) "http://localhost:3000/"
        else "http://docker.for.mac.host.internal:3000/"

    /** The current date used for data generation. */
    var currentDate = LocalDateTime.now()

    /** The current timer task used for data generation */
    var timerTask: TimerTask? = null

    init {
        config.generators[id] = this
    }

    @UseExperimental(ExperimentalStdlibApi::class)
    private fun formatData(data: IGeneratorValue): String {
        val cl = data::class
        val values = HashMap<String, String>()

        loop@ for (prop in cl.memberProperties) {
            val name = prop.name

            val value = prop.getter.call(data).toString()
            values.put(name, value)
        }

        val formatStringAscii = formatString.toCharArray()
        var i = 0
        var result = ""

        while (i < formatStringAscii.size) {
            if (formatStringAscii[i] != '$') {
                result += formatStringAscii[i++]
                continue
            }
            if (i + 1 == formatStringAscii.size || formatStringAscii[i + 1] != '{') {
                result += formatStringAscii[i++]
                continue
            }

            i += 2

            var ident = ""
            while (i < formatStringAscii.size && formatStringAscii[i] != '}') {
                ident += formatStringAscii[i++]
            }

            result += if (values.containsKey(ident)) {
                values[ident]
            } else {
                "<invalid datapoint: $ident>"
            }

            ++i
        }

        return result
    }

    private suspend fun sendMessageHTTP(value: IGeneratorValue) {
        when (dataType) {
            GeneratorDataType.JSON -> {
                config.client.post<String> {
                    url(endpoint)
                    contentType(ContentType.Application.Json)
                    body = value
                }
            }
            GeneratorDataType.FormatString -> {
                config.client.post<String> {
                    url(endpoint)
                    body = TextContent(formatData(value),
                        contentType = ContentType.Text.Plain)
                }
            }
        }
    }

    private fun sendMessageUDP(value: IGeneratorValue) {
        if (udpIPAddress == null) {
            val split = endpoint.split(":")
            if (split.size != 2) {
                println("invalid UDP address")
                return
            }

            udpIPAddress = InetAddress.getByName(split[0])

            try {
                udpPort = split[1].toInt()
            } catch (e: NumberFormatException) {
                println("invalid UDP port: ${e.message}")
                return
            }
        }

        val msg: String = when (dataType) {
            GeneratorDataType.JSON -> {
                config.gson.toJson(value)
            }
            GeneratorDataType.FormatString -> {
                formatData(value)
            }
        }

        val buf = msg.toByteArray()
        val packet = udpPort?.let { DatagramPacket(buf, 0, buf.size, udpIPAddress, it) }

        if (packet == null) {
            println("error creating UDP packet")
            return
        }

        config.udpSocket.send(packet)
    }

    private fun sendMessageCOAP(value: IGeneratorValue) {
        if (config.coapClient == null) {
            val split = endpoint.split(":")
            if (split.size != 2) {
                println("invalid CoaP address")
                return
            }

            val host = InetAddress.getByName(split[0])
            val port: Int

            try {
                port = split[1].toInt()
            } catch (e: NumberFormatException) {
                println("invalid CoaP port: ${e.message}")
                return
            }

            config.coapClient = CoapClientBuilder.newBuilder(InetSocketAddress(host, port)).build()
        }

        if (config.coapClient == null) {
            println("failed to create CoaP client for endpoint $endpoint")
            return
        }

        val msg: String = when (dataType) {
            GeneratorDataType.JSON -> {
                config.gson.toJson(value)
            }
            GeneratorDataType.FormatString -> {
                formatData(value)
            }
        }

        config.coapClient?.resource("/datapoint")?.payload(msg)?.put()
    }

    private fun reschedule() {
        if (!active) {
            return
        }

        timerTask = kotlin.concurrent.timerTask {
            GlobalScope.launch {
                val value = generator.generateValue(currentDate)
                if (protocol == GeneratorProtocol.HTTP) {
                    sendMessageHTTP(value)
                } else if (protocol == GeneratorProtocol.COAP) {
                    sendMessageCOAP(value)
                } else {
                    sendMessageUDP(value)
                }

                currentDate = currentDate.plus(granularity, ChronoUnit.MILLIS)
            }
        }

        config.timer.scheduleAtFixedRate(timerTask, 0, frequency)
    }
}

data class GeneratorEvent(val type: String, val timestamp: String, val data: JsonObject) {
    fun schedule(config: ApplicationConfig) {
        val date = if (timestamp.startsWith("+")) {
            config.startDate.plus(timestamp.toLong(), ChronoUnit.MILLIS)
        } else if (timestamp.matches(Regex("\\d+"))) {
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp.toLong()),
                TimeZone.getDefault().toZoneId())
        } else {
            LocalDateTime.parse(timestamp)
        }

        println("scheduling event '$type' at $date")

        val asDate = Date.from(date.atZone(ZoneId.systemDefault()).toInstant())
        config.timer.schedule(asDate) {
            handleEvent(config)
        }
    }

    private fun handleEvent(config: ApplicationConfig) {
        when (type) {
            "stop_all" -> config.generators.forEach { (_, value) -> value.active = false }
            "resume_all" -> config.generators.forEach { (_, value) -> value.active = true }
            "modify" -> {
                try {
                    if (!data.has("id")) {
                        println("'$type' event is missing 'id'")
                        return
                    }

                    val id = data["id"].asString
                    val seed = if (data.has("seed")) {
                        data["seed"].asLong
                    } else {
                        // Use a fixed seed to guarantee deterministic results
                        -1
                    }

                    val generator = if (config.generators.containsKey((id))) {
                        config.generators[id]!!
                    } else {
                        if (!data.has("kind")) {
                            println("'$type' event is missing 'kind'")
                            return
                        }

                        val baseGenerator = BaseGenerator.spawnGenerator(data["kind"].asString, config, seed, config.bucketName)
                        ActiveGenerator(id, config, baseGenerator)
                    }

                    if (data.has("endpoint")) {
                        generator.endpoint = data["endpoint"].asString
                    }

                    if (data.has("frequency")) {
                        generator.frequency = data["frequency"].asLong
                    }

                    if (data.has("events_per_second")) {
                        generator.frequency = 1000 / data["events_per_second"].asLong
                    }

                    if (data.has("granularity")) {
                        generator.granularity = data["granularity"].asLong
                    }

                    if (data.has("seconds_between_datapoints")) {
                        generator.granularity = (data["seconds_between_datapoints"].asFloat * 1000).toLong()
                    }

                    if (data.has("date")) {
                        generator.currentDate = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(data["date"].asLong),
                            TimeZone.getDefault().toZoneId())
                    }

                    if (data.has("format_string")) {
                        generator.dataType = GeneratorDataType.FormatString
                        generator.formatString = data["format_string"].asString
                    } else {
                        generator.dataType = GeneratorDataType.JSON
                    }

                    if (data.has("protocol")) {
                        val protocol = data["protocol"].asString.toLowerCase()
                        generator.protocol = if (protocol == "udp") {
                            GeneratorProtocol.UDP
                        } else if (protocol == "coap") {
                            GeneratorProtocol.COAP
                        } else if (protocol == "http") {
                            GeneratorProtocol.HTTP
                        } else {
                            println("invalid protocol $protocol")
                            GeneratorProtocol.HTTP
                        }
                    }

                    if (data.has("active")) {
                        generator.active = data["active"].asBoolean
                    }
                } catch (e: Exception) {
                    println(e.message)
                    return
                }
            }
            else -> println("unknown event type: '$type'")
        }
    }
}

class ApplicationConfig {
    /** The time when the server was started. */
    var startDate = LocalDateTime.now()

    /** The current event queue. */
    val events = mutableListOf<GeneratorEvent>()

    /** The active generators. */
    val generators = HashMap<String, ActiveGenerator>()

    /** The timer used for scheduling events. */
    val timer: Timer = Timer("generators")

    /** Client for making HTTP requests. */
    val client: HttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    /** The UDP client, if UDP is chosen as a protocol. */
    val udpSocket = DatagramSocket()

    /** The CoaP client, if CoaP is used. */
    var coapClient: CoapClient? = null

    /** GSON client. */
    val gson = Gson()

    /** The S3 client for downloading generator data. */
    var s3: AmazonS3? = null

    /** The S3 bucket name. */
    val bucketName: String

    init {
        bucketName = "fcp-ws19-generator-data-bucket"

        // Uncomment to upload generator data
        /*
        val config = ConfigurationProperties.fromResource("credentials")
        val awsCreds = BasicAWSCredentials(
            config[Key("aws_access_key_id", stringType)],
            config[Key("aws_secret_access_key", stringType)])

        s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(awsCreds))
            .withRegion("eu-north-1").build()

        bucketName = config[Key("bucket_name", stringType)]
        if (s3.doesBucketExistV2(bucketName)) {
            println("found existing bucket...")
        } else {
            try {
                println("creating bucket...")
                s3.createBucket(bucketName)
            } catch (e: AmazonS3Exception) {
                println(e.errorMessage)
                exitProcess(1)
            }
        }

        // For debugging only
        uploadGeneratorData(s3, bucketName, true)
        */
    }
}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun isRunningInDockerContainer(): Boolean {
    return try {
        val stream = Files.lines(Paths.get("/proc/1/cgroup"))
        stream.anyMatch { line -> line.contains("/docker") }
    } catch (e: Exception) {
        false
    }
}

@Suppress("unused")
fun uploadGeneratorData(s3Client: AmazonS3, bucketName: String, force: Boolean = false) {
    if (TemperatureGenerator.uploadResources(s3Client, bucketName, force)) {
        println("error uploading temperature data, exiting")
        exitProcess(1)
    }
    if (HeartRateGenerator.uploadResources(s3Client, bucketName, force)) {
        println("error uploading heart rate data, exiting")
        exitProcess(1)
    }
    if (PowerGenerator.uploadResources(s3Client, bucketName, force)) {
        println("error uploading power data, exiting")
        exitProcess(1)
    }
    if (TaxiFaresGenerator.uploadResources(s3Client, bucketName, force)) {
        println("error uploading taxi fare data, exiting")
        exitProcess(1)
    }
    if (TaxiRidesGenerator.uploadResources(s3Client, bucketName, force)) {
        println("error uploading taxi ride data, exiting")
        exitProcess(1)
    }
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }

    val appConfig = ApplicationConfig()
    val date = LocalDateTime.now()

    routing {
        val apiResource = this::class.java.classLoader.getResource("api/index.html").readText()
        get("/") {
            call.respondText(apiResource, contentType = ContentType.Text.Html)
        }

        // Health check used by the orchestrator to check whether or not the
        // generators are working correctly
        get("/healthcheck") {
            call.respond(HttpStatusCode.OK)
        }

        // Config
        route("/config") {
            post {
                appConfig.startDate = LocalDateTime.now()

                val events = call.receive<Array<GeneratorEvent>>()
                events.forEach { e -> e.schedule(appConfig) }
                appConfig.events.addAll(events)

                call.respond(HttpStatusCode.OK)
            }
        }

        /*
        // Temperature generator
        val temperatureGenerator = TemperatureGenerator(appConfig,-1, appConfig.bucketName)
        route("/temperature") {
            get("/random") {
                call.respond(temperatureGenerator.getRandomValue(date))
            }
            get("/random/{amount}") {
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    "day" -> call.respond(temperatureGenerator.getTemperaturesForDay(date))
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(temperatureGenerator.generateRandomValues(date, amount))
                    }
                }
            }
        }

        // Power Generator
        val powerGenerator = PowerGenerator(appConfig,-1, appConfig.bucketName)
        route("/power"){
            get("/random"){
                call.respond(powerGenerator.getRandomValue(date))
            }
            get("/random/{amount}"){
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(powerGenerator.generateRandomValues(date, amount))
                    }
                }
            }
        }

        // Taxi Fares Generator
        val taxiFaresGenerator = TaxiFaresGenerator(appConfig, -1, appConfig.bucketName)
        route("/taxiFares"){
            get("/random"){
                call.respond(taxiFaresGenerator.getRandomValue(date))
            }
            get("/random/{amount}") {
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(taxiFaresGenerator.generateRandomValues(date,amount))
                    }
                }
            }
        }

        // Taxi Rides Generator
        val taxiRidesGenerator = TaxiRidesGenerator(appConfig, -1, appConfig.bucketName)
        route("/taxiRides"){
            get("/random"){
                call.respond(taxiRidesGenerator.getRandomValue(date))
            }
            get("/random/{amount}") {
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(taxiRidesGenerator.generateRandomValues(date, amount))
                    }
                }
            }
        }

        // Heart Rate Generator
        val heartRateGenerator = HeartRateGenerator(appConfig, -1, appConfig.bucketName)
        route("/heartRate"){
            get("/random"){
                call.respond(heartRateGenerator.getRandomValue(date))
            }
            get("/random/{amount}") {
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(heartRateGenerator.generateRandomValues(date, amount))
                    }
                }
            }
        }*/
    }
}

