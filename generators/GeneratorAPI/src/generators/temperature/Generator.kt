package com.fcp.temperature

import com.fcp.generators.Generator
import com.google.gson.GsonBuilder
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.random.Random

data class MeanTemperature(val region: String, val meanTemp: Float) {

}

class TemperatureGenerator: Generator<Temperature>() {
    var region: String = "Bremen"
    var month: Int = 6
        set(value) {
            val monthMultiplierBounds = tempDistributionYear[value]
            this.monthMultiplier = randomFloat(monthMultiplierBounds.first,
                                               monthMultiplierBounds.second)

            field = value
        }

    private val meanTempData = HashMap<String, Float>()
    private val tempDistributionDay = floatArrayOf(
        -0.5f, // 12 AM
        -0.4f, // 1 AM
        -0.3f, // 2 AM
        -0.2f, // 3 AM
        -0.1f, // 4 AM
        0.0f, // 5 AM
        0.1f, // 6 AM
        0.2f, // 7 AM
        0.3f, // 8 AM
        0.4f, // 9 AM
        0.5f, // 10 AM
        0.6f, // 11 AM

        0.7f, // 12 PM
        0.7f, // 1 PM
        0.6f, // 2 PM
        0.5f, // 3 PM
        0.4f, // 4 PM
        0.3f, // 5 PM
        0.2f, // 6 PM
        0.1f, // 7 PM
        0.0f, // 8 PM
        -0.1f, // 9 PM
        -0.2f, // 10 PM
        -0.3f  // 11 PM
    )

    private val tempDistributionYear = arrayOf(
        Pair(-1.5f, -1f), // January
        Pair(-1.3f, -0.9f), // February
        Pair(-1.0f, -0.3f), // March
        Pair(-0.7f, -0.1f), // April
        Pair(-0.1f, 0.1f), // May
        Pair(0.3f, 0.7f), // June
        Pair(0.6f, 1.2f), // July
        Pair(1.3f, 1.8f), // August
        Pair(1.2f, 1.7f), // September
        Pair(-0.1f, 1.0f), // October
        Pair(-0.7f, 0.3f), // November
        Pair(-1.0f, -0.5f) // December
    )

    private var monthMultiplier = 0f

    init {
        val gsonBuilder = GsonBuilder().serializeNulls()
        val gson = gsonBuilder.create()
        val reader = File("resources/temperature/mean_temp.json").bufferedReader()
        val data = gson.fromJson(reader, Array<MeanTemperature>::class.java)

        for (temp in data) {
            meanTempData.set(temp.region, temp.meanTemp);
        }

        val monthMultiplierBounds = tempDistributionYear[month]
        this.monthMultiplier = randomFloat(monthMultiplierBounds.first,
            monthMultiplierBounds.second)
    }

    override fun getRandomValue(): Temperature {
        return getTemperatureForHour(Random.nextInt(0, 24))
    }

    fun getTemperatureForHour(hour: Int, hourWeight: Float = 1.0f): Temperature {
        val hour = max(min(24, hour), 0)
        val mean = meanTempData[region] ?: 10f

        // Adjust using an approximate temperature distribution throughout
        // the year in the northern hemisphere.
        val peakTemperature = mean + (monthMultiplier * mean)

        // Add small variations throughout the day so that the lowest temperature
        // is at about 12 AM and the highest at about 12 PM.
        var hourFactor = tempDistributionDay[hour] + hourWeight
        if (peakTemperature < 0) {
            hourFactor = -hourFactor
        }

        val result = peakTemperature + hourFactor * peakTemperature
        return Temperature(hour, result,
                           randomFloat(0f, 100f),
                           randomFloat(1000f, 1100f))
    }

    fun getTemperaturesForDay(): List<Temperature> {
        return generateRandomValues(24)
    }

    override fun generateRandomValues(amount: Int): List<Temperature> {
        println("generating data for region $region in month $month");

        var hour = 0
        val hourWeight = randomFloat(-0.3f, 0.3f)

        return List(amount) { getTemperatureForHour(hour++ % 24, hourWeight) }
    }
}