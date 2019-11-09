package com.fcp

import com.fcp.generators.BaseGenerator
import com.fcp.generators.GeneratorConfig
import com.fcp.generators.IGeneratorValue
import com.fcp.generators.power.PowerGenerator
import com.fcp.temperature.TemperatureGenerator
import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.request.receive
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.text.DateFormat

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun handleDatapoint(data: IGeneratorValue) {
    // TODO: send to endpoint
    println(data.toString())
}

suspend fun mainLoop(generators: MutableList<BaseGenerator>) {
    while (true) {
        for (gen in generators) {
            handleDatapoint(gen.generateValue())
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

    val generators = mutableListOf<BaseGenerator>()
    GlobalScope.launch {
        mainLoop(generators)
    }

    routing {
        get("/") {
            call.respondText("TODO: Swagger API here", contentType = ContentType.Text.Plain)
        }

        val temperatureGenerator = TemperatureGenerator()

        // Config
        route("/config") {
            // These GET routes are for debugging purposes only, POST routes will
            // be added once the configuration is final.
            get("/temperature/{region}/{month}") {
                val newRegion = call.parameters["region"]
                if (newRegion != null)
                {
                    temperatureGenerator.region = newRegion
                }

                val newMonth = call.parameters["month"]
                if (newMonth != null)
                {
                    temperatureGenerator.month =
                        newMonth.toIntOrNull() ?: temperatureGenerator.month
                }
            }

            post {
                val newGenerators = call.receive<Array<GeneratorConfig>>()
                for (gen in newGenerators) {
                    for (i in 0..gen.amount) {
                        val newGen: BaseGenerator? = when (gen.type) {
                            "Temperature" -> TemperatureGenerator()
                            "Power" -> PowerGenerator()
                            else -> null
                        }

                        if (newGen != null)
                            generators.add(newGen)
                    }
                }
            }
        }

        // Temperature generator
        route("/temperature") {
            get("/random") {
                call.respond(temperatureGenerator.getRandomValue());
            }
            get("/random/{amount}") {
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    "day" -> call.respond(temperatureGenerator.getTemperaturesForDay())
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(temperatureGenerator.generateRandomValues(amount))
                    }
                }
            }
        }

        val powerGenerator = PowerGenerator()
        // Power Generator
        route("/power"){
            get("/random"){
                call.respond(powerGenerator.getRandomValue())
            }
            get("/random/{amount}"){
                when (val amountStr = call.parameters["amount"]) {
                    null -> call.respond(HttpStatusCode.BadRequest)
                    else -> {
                        val amount = amountStr.toIntOrNull() ?: 1
                        call.respond(powerGenerator.generateRandomValues(amount))
                    }
                }
            }
        }
    }
}

