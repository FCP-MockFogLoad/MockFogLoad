package com.fcp

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.fcp.generators.BaseGenerator
import com.fcp.generators.heart.HeartRateGenerator
import com.fcp.generators.power.PowerGenerator
import com.fcp.generators.taxi.TaxiFaresGenerator
import com.fcp.generators.taxi.TaxiRidesGenerator
import com.fcp.temperature.TemperatureGenerator
import com.natpryce.konfig.*
import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.post
import io.ktor.client.features.json.*
import io.ktor.client.request.url
import io.ktor.request.receive
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DateFormat
import java.time.LocalDateTime
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.google.gson.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.schedule
import kotlin.system.exitProcess
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.fcp.generators.IGeneratorValue
import io.ktor.http.content.TextContent
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.typeOf

enum class GeneratorDataType {
    JSON,
    FormatString,
}

class ActiveGenerator(val id: String, val config: ApplicationConfig, val generator: BaseGenerator) {
    /** The generator's output data type. */
    var dataType: GeneratorDataType = GeneratorDataType.JSON

    /** The generator's format string (if dataType is FormatString). */
    var formatString: String = ""

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

            // prop as KProperty1<out IGeneratorValue, Any>
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

            if (values.containsKey(ident)) {
                result += values[ident]
            } else {
                result += "<invalid datapoint: $ident>"
            }

            ++i
        }

        println(result)
        return result
    }

    private fun reschedule() {
        if (!active) {
            return
        }

        timerTask = kotlin.concurrent.timerTask {
            GlobalScope.launch {
                when (dataType) {
                    GeneratorDataType.JSON -> {
                        config.client.post<String> {
                            url(endpoint)
                            contentType(ContentType.Application.Json)
                            body = generator.generateValue(currentDate)
                        }
                    }
                    GeneratorDataType.FormatString -> {
                        config.client.post<String> {
                            url(endpoint)
                            body = TextContent(formatData(generator.generateValue(currentDate)),
                                contentType = ContentType.Text.Plain)
                        }
                    }
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
                    val generator = if (config.generators.containsKey((id))) {
                        config.generators[id]!!
                    } else {
                        if (!data.has("kind")) {
                            println("'$type' event is missing 'kind'")
                            return
                        }

                        val baseGenerator = when (val kind = data["kind"].asString) {
                            "Temperature" -> TemperatureGenerator(config.s3, config.bucketName)
                            "Power" -> PowerGenerator(config.s3, config.bucketName)
                            "TaxiFares" -> TaxiFaresGenerator(config.s3, config.bucketName)
                            "TaxiRides" -> TaxiRidesGenerator(config.s3, config.bucketName)
                            "HeartRate" -> HeartRateGenerator(config.s3, config.bucketName)
                            else -> {
                                println("invalid generator kind '$kind'")
                                return
                            }
                        }

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

    /** The S3 client for downloading generator data. */
    val s3: AmazonS3

    /** The S3 bucket name. */
    val bucketName: String

    init {
 //      s3 = AmazonS3ClientBuilder.defaultClient()
       bucketName = ""

//        val config = ConfigurationProperties.fromResource("credentials")
//        val awsCreds = BasicAWSCredentials(
//            config[Key("aws_access_key_id", stringType)],
//            config[Key("aws_secret_access_key", stringType)])
//
        s3 = AmazonS3ClientBuilder.standard()
//            .withCredentials(AWSStaticCredentialsProvider(awsCreds))
            .withRegion("eu-north-1").build()
//
//        bucketName = config[Key("bucket_name", stringType)]
//        if (s3.doesBucketExistV2(bucketName)) {
//            println("found existing bucket...")
//        } else {
//            try {
//                println("creating bucket...")
//                s3.createBucket(bucketName)
//            } catch (e: AmazonS3Exception) {
//                println(e.errorMessage)
//                exitProcess(1)
//            }
//        }
//
//        // For debugging only
//        uploadGeneratorData(s3, bucketName)
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
fun uploadGeneratorData(s3Client: AmazonS3, bucketName: String) {
    if (TemperatureGenerator.uploadResources(s3Client, bucketName)) {
        println("error uploading temperature data, exiting")
        exitProcess(1)
    }
    if (HeartRateGenerator.uploadResources(s3Client, bucketName)) {
        println("error uploading heart rate data, exiting")
        exitProcess(1)
    }
    if (PowerGenerator.uploadResources(s3Client, bucketName)) {
        println("error uploading power data, exiting")
        exitProcess(1)
    }
    if (TaxiFaresGenerator.uploadResources(s3Client, bucketName)) {
        println("error uploading taxi fare data, exiting")
        exitProcess(1)
    }
    if (TaxiRidesGenerator.uploadResources(s3Client, bucketName)) {
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

        // Temperature generator
        /* val temperatureGenerator = TemperatureGenerator(appConfig.s3, appConfig.bucketName)
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
        val powerGenerator = PowerGenerator(appConfig.s3, appConfig.bucketName)
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
        val taxiFaresGenerator = TaxiFaresGenerator(appConfig.s3, appConfig.bucketName)
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
        val taxiRidesGenerator = TaxiRidesGenerator(appConfig.s3, appConfig.bucketName)
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
         */

        // Heart Rate Generator
        val heartRateGenerator = HeartRateGenerator(appConfig.s3, appConfig.bucketName)
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
        }
    }
}

