package com.fcp

import com.fcp.generators.BaseGenerator
import com.fcp.generators.GeneratorConfig
import com.fcp.generators.IGeneratorValue
import com.fcp.generators.heart.HeartRateGenerator
import com.fcp.generators.power.PowerGenerator
import com.fcp.generators.taxi.TaxiFaresGenerator
import com.fcp.generators.taxi.TaxiRidesGenerator
import com.fcp.temperature.TemperatureGenerator
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

suspend fun mainLoop(date: LocalDateTime, generators: MutableList<BaseGenerator>, client: HttpClient) {
    while (true) {
        for (gen in generators) {
            handleDatapoint(gen.generateValue(date), client)
        }

        delay(100)
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
//        mainLoop(mutex, date, generators, client)
//        date = date.plusHours(1)
    }

    routing {
        val apiResource = this::class.java.classLoader.getResource("api/index.html").readText()
        get("/") {
            call.respondText(apiResource, contentType = ContentType.Text.Html)
        }

        val temperatureGenerator = TemperatureGenerator()

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
                            "Temperature" -> TemperatureGenerator()
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

