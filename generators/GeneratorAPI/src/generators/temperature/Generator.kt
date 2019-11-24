package com.fcp.temperature

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.fcp.generators.Generator
import com.google.gson.GsonBuilder
import java.time.LocalDateTime
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

data class TemperatureDataPoint(val date: String, val temp: Float) {}
data class TemperatureData(val mean: Float,
                           val datapoints: Array<TemperatureDataPoint>,
                           val region: String) {}

class TemperatureGenerator(s3: AmazonS3, bucketName: String): Generator<Temperature>("Temperature") {
    var region: String = "BerlinDahlem"
    val variation = 5f
    private var meanTemperatures: MutableList<Float>

    init {
        meanTemperatures = getMeanTemperatures(s3, bucketName, region)
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

        fun uploadResources(s3: AmazonS3, bucketName: String): Boolean {
            for (region in regions) {
                val resource = this::class.java.classLoader.getResource("temperature/" + region + ".json").readText()
                println("uploading $region...")

                try {
                    s3.putObject(bucketName, "temperature/" + region, resource)
                    println("done!")
                } catch (e: AmazonServiceException) {
                    println(e.message)
                    return true
                }
            }

            return false
        }

        fun getMeanTemperatures(s3: AmazonS3, bucketName: String, region: String): MutableList<Float> {
            var list = meanTemperatures[region]
            if (list != null) {
                return list
            }

            val dataStr: String = try {
                val resource = s3.getObject(bucketName, "temperature/" + region)
                val sc = Scanner(resource.objectContent)
                val sb = StringBuffer()
                while (sc.hasNext()) {
                    sb.append(sc.nextLine())
                }

                sb.toString()
            } catch (e: Exception) {
                print(e.message)
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