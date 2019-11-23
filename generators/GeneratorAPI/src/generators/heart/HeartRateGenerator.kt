package com.fcp.generators.heart

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import com.fcp.generators.taxi.TaxiFares
import java.io.File
import kotlin.collections.listOf

data class HeartRate(val age: Float,
                     val sex: Float,
                     val chestPainLevel: Float,
                     val bloodPressure: Float,
                     val cholestoral: Float,
                     val bloodSugar: Float,
                     val electroCardiographic: Float,
                     val heartRate: Float,
                     val angina: Float,
                     val oldPeak: Float) : IGeneratorValue{

    override val unit: String
        get() =  ""

    override val value: Float
        get() = heartRate
}

class HeartRateGenerator: Generator<HeartRate>("HeartRate"){

    var heartRate = listOf<HeartRate>()

    init {
        val file = File("resources/heartRate/heart.dat")
        heartRate = readFileAndStoreInList(file)
    }


    private fun readFileAndStoreInList(file: File) : List<HeartRate>
            = file.useLines { it.map { line :String -> mapToHeartRate(line)}.toList() }

    private fun mapToHeartRate(line : String) : HeartRate{
        var result: List<String> = line.split(" ").map { it.trim() }
        return HeartRate(
            result.get(0).toFloat(),
            result.get(1).toFloat(),
            result.get(2).toFloat(),
            result.get(3).toFloat(),
            result.get(4).toFloat(),
            result.get(5).toFloat(),
            result.get(6).toFloat(),
            result.get(7).toFloat(),
            result.get(8).toFloat(),
            result.get(9).toFloat())
    }

    override fun getRandomValue(): HeartRate {
        return heartRate.random()
    }

    override fun generateRandomValues(amount: Int): List<HeartRate> {
        return List(amount) { getRandomValue() }
    }

}
