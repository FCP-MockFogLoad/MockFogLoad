package com.fcp.generators.heart

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.io.File
import java.time.LocalDateTime
import kotlin.collections.listOf

data class HeartRate(val age: Float,
                     val sex: String,
                     val chestPainLevel: Float,
                     val bloodPressure: Float,
                     val cholestoral: Float,
                     val bloodSugar: Float,
                     val electroCardiographic: Float,
                     val heartRate: Float,
                     val angina: Float,
                     val oldPeak: Float,
                     override val date: LocalDateTime
) : IGeneratorValue{

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
        var gender: String
        if(result.get(1).toFloat() == 0.0f){
            gender = "Male"
        }else
            gender = "Female"

        return HeartRate(
            result.get(0).toFloat(),
            gender,
            result.get(2).toFloat(),
            result.get(3).toFloat(),
            result.get(4).toFloat(),
            result.get(5).toFloat(),
            result.get(6).toFloat(),
            result.get(7).toFloat(),
            result.get(8).toFloat(),
            result.get(9).toFloat(),
            LocalDateTime.now())
    }

    override fun getRandomValue(date: LocalDateTime): HeartRate {
        return heartRate.random()
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<HeartRate> {
        return List(amount) { getRandomValue(date) }
    }

}
