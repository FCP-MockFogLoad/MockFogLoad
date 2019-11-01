package com.fcp.temperature

import com.fcp.generators.Generator
import com.google.gson.GsonBuilder
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.math.abs

data class MeanTemperature(val region: String, val meanTemp: Float) {

}

class TemperatureGenerator(var region: String = "Bremen", var month: Int = 6): Generator<Temperature>() {
    private val meanTempData = HashMap<String, Float>()
    private val tempDistributionDay = Array(24) { Pair(0f, 0f) }
    private val tempDistributionYear = Array(12) { Pair(0f, 0f) }

    init {
        val gsonBuilder = GsonBuilder().serializeNulls()
        val gson = gsonBuilder.create()
        val reader = File("resources/temperature/mean_temp.json").bufferedReader()
        val data = gson.fromJson(reader, Array<MeanTemperature>::class.java)

        for (temp in data) {
            meanTempData.set(temp.region, temp.meanTemp);
        }

        tempDistributionDay[0] = Pair(-0.6f, -0.3f) // 12:00 AM
        tempDistributionDay[1] = Pair(-0.7f, -0.3f) // 1:00 AM
        tempDistributionDay[2] = Pair(-0.8f, -0.35f) // 2:00 AM
        tempDistributionDay[3] = Pair(-0.8f, -0.4f) // 3:00 AM
        tempDistributionDay[4] = Pair(-0.7f, -0.4f) // 4:00 AM
        tempDistributionDay[5] = Pair(-0.5f, -0.3f) // 5:00 AM
        tempDistributionDay[6] = Pair(-0.4f, -0.3f) // 6:00 AM
        tempDistributionDay[7] = Pair(-0.3f, -0.1f) // 7:00 AM
        tempDistributionDay[8] = Pair(-0.2f, 0.05f) // 8:00 AM
        tempDistributionDay[9] = Pair(-0.15f, 0.1f) // 9:00 AM
        tempDistributionDay[10] = Pair(-0.15f, 0.2f) // 10:00 AM
        tempDistributionDay[11] = Pair(-0.1f, 0.05f) // 11:00 AM

        tempDistributionDay[12] = Pair(-0.1f, 0.1f) // 12:00 PM
        tempDistributionDay[13] = Pair(-0.1f, 0.2f) // 1:00 PM
        tempDistributionDay[14] = Pair(0.0f, 0.3f) // 2:00 PM
        tempDistributionDay[15] = Pair(0.1f, 0.35f) // 3:00 PM
        tempDistributionDay[16] = Pair(0.05f, 0.3f) // 4:00 PM
        tempDistributionDay[17] = Pair(0.0f, 0.25f) // 5:00 PM
        tempDistributionDay[18] = Pair(-0.05f, 0.2f) // 6:00 PM
        tempDistributionDay[19] = Pair(-0.2f, 0.1f) // 7:00 PM
        tempDistributionDay[20] = Pair(-0.3f, 0.0f) // 8:00 PM
        tempDistributionDay[21] = Pair(-0.4f, -0.1f) // 9:00 PM
        tempDistributionDay[22] = Pair(-0.45f, -0.15f) // 10:00 PM
        tempDistributionDay[23] = Pair(-0.5f, -0.2f) // 11:00 PM

        tempDistributionYear[0] = Pair(-1.5f, -1f) // January
        tempDistributionYear[1] = Pair(-1.3f, -0.9f) // February
        tempDistributionYear[2] = Pair(-1.0f, -0.3f) // March
        tempDistributionYear[3] = Pair(-0.7f, -0.1f) // April
        tempDistributionYear[4] = Pair(-0.1f, 0.1f) // May
        tempDistributionYear[5] = Pair(0.3f, 0.7f) // June
        tempDistributionYear[6] = Pair(0.6f, 1.2f) // July
        tempDistributionYear[7] = Pair(1.3f, 1.8f) // August
        tempDistributionYear[8] = Pair(1.2f, 1.7f) // September
        tempDistributionYear[9] = Pair(-0.1f, 1.0f) // October
        tempDistributionYear[10] = Pair(-0.7f, 0.3f) // November
        tempDistributionYear[11] = Pair(-1.0f, -0.5f) // December
    }

    override fun getRandomValue(): Temperature {
        return Temperature(meanTempData[region] ?: -1.0f, randomFloat(0f, 100f),
                           randomFloat(1000f, 1100f));
    }

    fun getTemperaturesForHour(hour: Int): Temperature {
        val hourClean = max(min(24, hour), 0)

        val mean = meanTempData[region] ?: 10f
        val hourMultiplierBounds = tempDistributionDay[hourClean]
        val monthMultiplierBounds = tempDistributionYear[month]

        val hourMultiplier = randomFloat(hourMultiplierBounds.first,
                                         hourMultiplierBounds.second)

        val monthMultiplier = randomFloat(monthMultiplierBounds.first,
                                          monthMultiplierBounds.second)

        val peakTemperature = mean + (monthMultiplier * mean)
        val result = peakTemperature + (hourMultiplier * peakTemperature)

        return Temperature(result, 0f, 0f)
    }

    fun getTemperaturesForDay(): Array<Temperature> {
        println("generating data for region $region in month $month");

        var hour = 0
        return Array(24) { getTemperaturesForHour(hour++) }
    }
}