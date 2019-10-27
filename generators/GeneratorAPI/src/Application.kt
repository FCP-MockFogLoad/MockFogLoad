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

        // Temperature generator
        route("/temperature") {
            get("/random") {
                var generator = TemperatureGenerator();
                call.respond(generator.getRandomValue());
            }
            get("/random/{amount}") {
                var amountStr = call.parameters["amount"];
                if (amountStr == null)
                {
                    call.respond(HttpStatusCode.BadRequest);
                }
                else
                {
                    var amount = amountStr.toInt();
                    var generator = TemperatureGenerator();
                    call.respond(generator.generateRandomValues(amount));
                }

            }
        }
    }
}

