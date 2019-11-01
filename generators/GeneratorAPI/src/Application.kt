package com.fcp

import com.fcp.temperature.TemperatureGenerator
import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*

import java.text.DateFormat

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }

    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        val temperatureGenerator = TemperatureGenerator("Bremen", 0);

        // Config
        route("/config") {
            // These GET routes are for debugging purposes only, POST routes will
            // be added once the configuration is final.
            get("/temperature/{region}/{month}") {
                val newRegion = call.parameters["region"];
                if (newRegion != null)
                {
                    temperatureGenerator.region = newRegion;
                }

                val newMonth = call.parameters["month"];
                if (newMonth != null)
                {
                    temperatureGenerator.month =
                        newMonth.toIntOrNull() ?: temperatureGenerator.month;
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
                        val amount = amountStr.toInt();
                        call.respond(temperatureGenerator.generateRandomValues(amount));
                    }
                }

            }
        }
    }
}

