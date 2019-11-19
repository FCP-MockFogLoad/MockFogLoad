package com.fcp.temperature

import com.fcp.generators.Generator
import com.google.gson.GsonBuilder
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

data class TemperatureDataPoint(val date: String, val temp: Float) {}
data class TemperatureData(val mean: Float,
                           val datapoints: Array<TemperatureDataPoint>,
                           val region: String) {}

class TemperatureGenerator: Generator<Temperature>("Temperature") {
    var region: String = "BerlinDahlem"
    val variation = 5f

    private var meanTemperatures = mutableListOf<Float>()

    init {
        val gsonBuilder = GsonBuilder().serializeNulls()
        val gson = gsonBuilder.create()

        val resource = this::class.java.classLoader.getResource("temperature/" + region + ".json").readText()
        val data = gson.fromJson(resource, TemperatureData::class.java)

        var n = 0
        for (dp in data.datapoints) {
            // Ignore leap days.
            if (dp.date.endsWith("0229")) {
                continue
            }

            meanTemperatures.add(dp.temp)

            if (dp.date.endsWith("1231")) {
                // Only use full years.
                if (data.datapoints.size - n < 365) {
                    break
                }
            }

            ++n
        }
    }

    private fun generateTemperature(date: LocalDateTime): Float {
        val mean = meanTemperatures[date.dayOfYear]

        // Use a sin curve to approximate temperature distribution throughout
        // the day, assuming the highest temperature is at 12 PM and
        // the lowest at 12 AM.
        val minuteOfDay = date.hour * 60f + date.minute
        val shift = sin((minuteOfDay / (24f * 60f)) * 2f * PI + (1.5f * PI))

        return mean + (shift * variation).toFloat()
    }

    override fun getRandomValue(date: LocalDateTime): Temperature {
        return Temperature(date, generateTemperature(date))
    }

    fun getTemperaturesForDay(date: LocalDateTime): List<Temperature> {
        return generateRandomValues(date, 24)
    }
}