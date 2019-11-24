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
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.ConfigFactory.systemProperties
import com.amazonaws.auth.BasicAWSCredentials
import sun.security.krb5.internal.Krb5.getErrorMessage
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.Bucket
import kotlin.system.exitProcess


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun isRunningInDockerContainer(): Boolean {
    return try {
        val stream = Files.lines(Paths.get("/proc/1/cgroup"))
        stream.anyMatch { line -> line.contains("/docker") }
    } catch (e: Exception) {
        false
    }
}

suspend fun handleDatapoint(data: IGeneratorValue, client: HttpClient) {
    client.post<String> {
        url(if (!isRunningInDockerContainer()) "http://localhost:3000/"
            else "http://docker.for.mac.host.internal:3000/")

        contentType(ContentType.Application.Json)
        body = data
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

    val config = ConfigurationProperties.fromResource("credentials")
    val awsCreds = BasicAWSCredentials(
        config[Key("aws_access_key_id", stringType)],
        config[Key("aws_secret_access_key", stringType)])

    val s3Client = AmazonS3ClientBuilder.standard()
        .withCredentials(AWSStaticCredentialsProvider(awsCreds))
        .withRegion("eu-north-1")
        .build()

    val bucketName = config[Key("bucket_name", stringType)]
    val b = if (s3Client.doesBucketExistV2(bucketName)) {
        println("found existing bucket...")
        s3Client.listBuckets().first { bucket -> bucket.name == bucketName }
    } else {
        try {
            println("creating bucket...")
            s3Client.createBucket(bucketName)
        } catch (e: AmazonS3Exception) {
            println(e.errorMessage)
            exitProcess(1)
        }
    }

//    if (TemperatureGenerator.uploadResources(s3Client, bucketName)) {
//        println("error uploading temperature data, exiting")
//        exitProcess(1)
//    }

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
                    handleDatapoint(gen.generateValue(date), client)
                }
            }

            delay(100)
            date = date.plusHours(1)
        }
    }

    routing {
        val apiResource = this::class.java.classLoader.getResource("api/index.html").readText()
        get("/") {
            call.respondText(apiResource, contentType = ContentType.Text.Html)
        }

        val temperatureGenerator = TemperatureGenerator(s3Client, bucketName)

        // Config
        route("/config") {
            // These GET routes are for debugging purposes only, POST routes will
            // be added once the configuration is final.
            get("/temperature/{region}") {
                val newRegion = call.parameters["region"]
                if (newRegion != null)
                {
                    temperatureGenerator.region = newRegion
                }
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
                            "HeartRate" -> HeartRateGenerator()
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
        route("/temperature") {
            get("/random") {
                call.respond(temperatureGenerator.getRandomValue(date));
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

        val powerGenerator = PowerGenerator()
        // Power Generator
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

        val taxiFaresGenerator = TaxiFaresGenerator()
        // Taxi Fares Generator
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

        val taxiRidesGenerator = TaxiRidesGenerator()
        // Taxi Rides Generator
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

        val heartRateGenerator = HeartRateGenerator()
        // Heart Rate Generator
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

