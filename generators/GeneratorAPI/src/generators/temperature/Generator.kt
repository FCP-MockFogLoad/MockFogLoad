package com.fcp.temperature

import com.amazonaws.services.s3.AmazonS3
import com.fcp.ApplicationConfig
import com.fcp.generators.Generator
import com.google.gson.GsonBuilder
import java.time.LocalDateTime
import java.util.*
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

data class TemperatureDataPoint(val date: String, val temp: Float) {}
data class TemperatureData(val mean: Float,
                           val datapoints: Array<TemperatureDataPoint>,
                           val region: String) {}

class TemperatureGenerator(app: ApplicationConfig, seed: Long, bucketName: String): Generator<Temperature>("Temperature", app, seed) {
    var region: String
    val variation = 5f
    private var meanTemperatures: MutableList<Float>

    init {
        region = regions[Random.nextInt(0, regions.size)]
        meanTemperatures = getMeanTemperatures(bucketName, region)
    }

    companion object {
        val regions = arrayOf(
            "BerlinDahlem",
            "Bremen",
            "Frankfurt",
            "Salzburg",
            "Wien"
        )

        val meanTemperatures = HashMap<String, MutableList<Float>>()

        @Suppress("unused")
        fun uploadResources(s3: AmazonS3, bucketName: String, force: Boolean = false): Boolean {
            for (region in regions) {
                if (uploadResource(s3, bucketName, "temperature/$region.json",
                              "temperature/$region", force)) {
                    return true
                }
            }

            return false
        }

        fun getMeanTemperatures(bucketName: String, region: String): MutableList<Float> {
            var list = meanTemperatures[region]
            if (list != null) {
                return list
            }

            val dataStr: String = try {
                loadResourceHTTP(bucketName, "temperature/$region")
            } catch (e: Exception) {
                println(e.message)
                "{}"
            }

            val gsonBuilder = GsonBuilder().serializeNulls()
            val gson = gsonBuilder.create()
            val data = gson.fromJson(dataStr, TemperatureData::class.java)

            list = mutableListOf<Float>()

            var n = 0
            for (dp in data.datapoints) {
                // Ignore leap days.
                if (dp.date.endsWith("0229")) {
                    continue
                }

                list.add(dp.temp)

                if (dp.date.endsWith("1231")) {
                    // Only use full years.
                    if (data.datapoints.size - n < 365) {
                        break
                    }
                }

                ++n
            }

            meanTemperatures[region] = list
            return list
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