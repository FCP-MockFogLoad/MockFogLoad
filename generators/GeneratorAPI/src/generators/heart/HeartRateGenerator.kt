package com.fcp.generators.heart

import com.amazonaws.services.s3.AmazonS3
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
) : IGeneratorValue {
    override val unit: String
        get() =  ""

    override val value: Float
        get() = heartRate
}

class HeartRateGenerator(s3: AmazonS3?, bucketName: String): Generator<HeartRate>("HeartRate") {
    init {
        if (heartRate == null) {
            initializeHeartRateData(s3, bucketName)
        }
    }

    companion object {
        private var heartRate: List<HeartRate>? = null

        private fun initializeHeartRateData(s3: AmazonS3?, bucketName: String) {
            val resource = loadResourceHTTP(bucketName, "heartrate")
            heartRate = resource.split("\n")
                .map { line -> try { this.mapToHeartRate(line) } catch (e: Exception) {
                    HeartRate(0f, "", 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, LocalDateTime.now()) } }.toList()
        }

        private fun mapToHeartRate(line : String) : HeartRate {
            val result: List<String> = line.split(" ").map { it.trim() }
            val gender = if (result.get(1).toFloat() == 0.0f){
                "Male"
            } else {
                "Female"
            }

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

        @Suppress("unused")
        fun uploadResources(s3: AmazonS3, bucketName: String, force: Boolean = false): Boolean {
            return uploadResource(s3, bucketName,
                      "heartRate/heart.dat", "heartrate", force)
        }
    }

    override fun getRandomValue(date: LocalDateTime): HeartRate {
        return heartRate!!.random()
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<HeartRate> {
        return List(amount) { getRandomValue(date) }
    }

}
