package com.fcp.temperature

import com.fcp.generators.Generator
import com.google.gson.GsonBuilder
import java.io.File

data class MeanTemperature(val region: String, val meanTemp: Float) {

}

class TemperatureGenerator(val region: String = "Bremen"): Generator<Temperature>() {
    val meanTempData = HashMap<String, Float>()

    init {
        val gsonBuilder = GsonBuilder().serializeNulls()
        val gson = gsonBuilder.create()
        val reader = File("resources/temperature/mean_temp.json").bufferedReader()
        val data = gson.fromJson(reader, Array<MeanTemperature>::class.java)

        for (temp in data) {
            meanTempData.set(temp.region, temp.meanTemp);
        }
    }

    override fun getRandomValue(): Temperature {
        return Temperature(meanTempData[region] ?: -1.0f, randomFloat(0f, 100f),
                           randomFloat(1000f, 1100f));
    }
}