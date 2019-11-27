package com.fcp

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.fcp.generators.BaseGenerator
import com.fcp.generators.GeneratorConfig
import com.fcp.generators.IGeneratorValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DateFormat
import java.time.LocalDateTime
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.AmazonS3Exception
import kotlin.system.exitProcess

data class ApplicationConfig(var endpoints: Array<String> = arrayOf(),
                             var delay: Long = 100) {
    fun copy(other: ApplicationConfig) {
        this.endpoints = other.endpoints
        this.delay = other.delay
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplicationConfig

        if (!endpoints.contentEquals(other.endpoints)) return false
        if (delay != other.delay) return false

        return true
    }

    override fun hashCode(): Int {
        var result = endpoints.contentHashCode()
        result = 31 * result + delay.hashCode()
        return result
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

suspend fun handleDatapoint(config: ApplicationConfig, data: IGeneratorValue, client: HttpClient) {
    for (endpoint in config.endpoints) {
        client.post<String> {
            url(endpoint)
            contentType(ContentType.Application.Json)
            body = data
        }
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

    val appConfig = ApplicationConfig(arrayOf(
        if (!isRunningInDockerContainer()) "http://localhost:3000/"
        else "http://docker.for.mac.host.internal:3000/"))

    val config = ConfigurationProperties.fromResource("credentials")
    val awsCreds = BasicAWSCredentials(
        config[Key("aws_access_key_id", stringType)],
        config[Key("aws_secret_access_key", stringType)])

    val s3Client = AmazonS3ClientBuilder.standard()
        .withCredentials(AWSStaticCredentialsProvider(awsCreds))
        .withRegion("eu-north-1")
        .build()

    val bucketName = config[Key("bucket_name", stringType)]
    if (s3Client.doesBucketExistV2(bucketName)) {
        println("found existing bucket...")
    } else {
        try {
            println("creating bucket...")
            s3Client.createBucket(bucketName)
        } catch (e: AmazonS3Exception) {
            println(e.errorMessage)
            exitProcess(1)
        }
    }

    // For debugging only
    uploadGeneratorData(s3Client, bucketName)

    val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    val mutex = Mutex()
    var date = LocalDateTime.parse("2000-01-01T00:00:00")
    val generators = mutableListOf<BaseGenerator>()

    GlobalScope.launch {
        while (true) {
            mutex.withLock {
                for (gen in generators) {
                    handleDatapoint(appConfig, gen.generateValue(date), client)
                }
            }

            delay(appConfig.delay)
            date = date.plusHours(1)
        }
    }

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
                val newConfig = call.receive<ApplicationConfig>()
                appConfig.copy(newConfig)
            }

            post("/spawn") {
                val newGenerators = call.receive<Array<GeneratorConfig>>()
                for (gen in newGenerators) {
                    for (i in 0..gen.amount) {
                        val newGen: BaseGenerator? = when (gen.type) {
                            "Temperature" -> TemperatureGenerator(s3Client, bucketName)
                            "Power" -> PowerGenerator()
                            "TaxiFares" -> TaxiFaresGenerator()
                            "TaxiRides" -> TaxiRidesGenerator()
                            "HeartRate" -> HeartRateGenerator(s3Client, bucketName)
                            else -> null
                        }

                        if (newGen != null) {
                            mutex.withLock {
                                generators.add(newGen)
                            }
                        }
                    }
                }
            }

            post("/clear") {
                mutex.withLock {
                    generators.clear()
                }
            }
        }

        // Temperature generator
        val temperatureGenerator = TemperatureGenerator(s3Client, bucketName)
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
        val powerGenerator = PowerGenerator()
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
        val taxiFaresGenerator = TaxiFaresGenerator()
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
        val taxiRidesGenerator = TaxiRidesGenerator()
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
        val heartRateGenerator = HeartRateGenerator(s3Client, bucketName)
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

