package com.fcp.generators

import com.amazonaws.services.s3.AmazonS3
import com.fcp.ApplicationConfig
import java.time.LocalDateTime

data class HeartRate(val age: Int,
                     val sex: String,
                     val chestPainLevel: Float,
                     val bloodPressure: Float,
                     val cholestoral: Float,
                     val bloodSugar: Float,
                     val electroCardiographic: Float,
                     val heartRate: Float,
                     val angina: Float,
                     val oldPeak: Float,
                     override val date: LocalDateTime,
                     val activity: String = ""
) : IGeneratorValue {
    override val unit: String
        get() =  ""

    override val value: Float
        get() = heartRate
}

class HeartRateGenerator(app: ApplicationConfig, seed: Long, bucketName: String): Generator<HeartRate>("HeartRate", app, seed) {
    enum class Activity {
        Idle,
        Exercise,
        Sleep;
    }

    var currentActivity: Activity =
        Activity.Idle
    var currentActivityStart: LocalDateTime
    var currentActivityEnd: LocalDateTime
    var baseHeartRate: HeartRate

    init {
        if (heartRate == null) {
            initializeHeartRateData(bucketName)
        }

        // Use this as a basis for age & gender
        this.baseHeartRate = heartRate!!.random(this.random)

        this.currentActivityStart = app.startDate
        this.currentActivityEnd = app.startDate

        scheduleRandomActivity()
    }

    companion object {
        private var heartRate: List<HeartRate>? = null

        init {
            registerGeneratorType("HeartRate", HeartRateGenerator::class)
        }

        private fun initializeHeartRateData(bucketName: String) {
            val resource = loadResourceHTTP(bucketName, "heartrate")
            heartRate = resource.split("\n")
                .map { line -> try {
                    mapToHeartRate(line)
                } catch (e: Exception) {
                    println(e.message)
                    HeartRate(
                        0, "", 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, LocalDateTime.now()
                    )
                } }.toList()
        }

        private fun mapToHeartRate(line : String) : HeartRate {
            val result: List<String> = line.split(" ").map { it.trim() }
            val gender = if (result.get(1).toFloat() == 0.0f) {
                "Male"
            } else {
                "Female"
            }

            return HeartRate(
                result.get(0).toFloat().toInt(),
                gender,
                result.get(2).toFloat(),
                result.get(3).toFloat(),
                result.get(4).toFloat(),
                result.get(5).toFloat(),
                result.get(6).toFloat(),
                result.get(7).toFloat(),
                result.get(8).toFloat(),
                result.get(9).toFloat(),
                LocalDateTime.now()
            )
        }

        @Suppress("unused")
        fun uploadResources(s3: AmazonS3, bucketName: String, force: Boolean = false): Boolean {
            return uploadResource(s3, bucketName,
                      "heartRate/heart.dat", "heartrate", force)
        }
    }

    private fun scheduleRandomActivity() {
        val act = this.random.nextInt(0, 3)
        scheduleActivity(Activity.values()[act])
    }

    private fun scheduleActivity(kind: Activity) {
        this.currentActivity = kind

        // Source: https://healthsolutions.fitbit.com/blog/resting-heart-rate/
        var heartRate = when (baseHeartRate.age) {
            in 0..20 -> random.nextInt(55, 62)
            in 20..30 -> random.nextInt(60, 65)
            in 30..40 -> random.nextInt(62, 67)
            in 40..50 -> random.nextInt(65, 68)
            in 50..60 -> random.nextInt(62, 65)
            else -> random.nextInt(59, 65)
        }

        // Assume obesity rate of 30%
        if (random.nextInt(100) < 30) {
            heartRate += 15
        }

        if (baseHeartRate.sex == "Female") {
            heartRate += 4
        }

        // Duration in minutes
        val activityDuration: Int
        when (kind) {
            Activity.Idle -> {
                activityDuration = random.nextInt(30, 600)
            }
            Activity.Exercise -> {
                heartRate += random.nextInt(40, 100)
                activityDuration = random.nextInt(10, 120)
            }
            Activity.Sleep -> {
                heartRate -= 10
                activityDuration = random.nextInt(300, 600)
            }
        }

        val prevEnd = currentActivityEnd
        currentActivityEnd = currentActivityStart.plusMinutes(activityDuration.toLong())
        currentActivityStart = prevEnd

        baseHeartRate = HeartRate(
            baseHeartRate.age,
            baseHeartRate.sex,
            baseHeartRate.chestPainLevel,
            baseHeartRate.bloodPressure,
            baseHeartRate.cholestoral,
            baseHeartRate.bloodSugar,
            baseHeartRate.electroCardiographic,
            heartRate.toFloat(),
            baseHeartRate.angina,
            baseHeartRate.oldPeak,
            currentActivityStart
        )
    }

    override fun getRandomValue(date: LocalDateTime): HeartRate {
        if (date > currentActivityEnd) {
            scheduleRandomActivity()
        }

        return HeartRate(
            baseHeartRate.age,
            baseHeartRate.sex,
            baseHeartRate.chestPainLevel,
            baseHeartRate.bloodPressure,
            baseHeartRate.cholestoral,
            baseHeartRate.bloodSugar,
            baseHeartRate.electroCardiographic,
            baseHeartRate.heartRate + randomFloat(-.5f, .5f),
            baseHeartRate.angina,
            baseHeartRate.oldPeak,
            currentActivityStart,
            currentActivity.toString()
        )
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<HeartRate> {
        return List(amount) { getRandomValue(date) }
    }

}
