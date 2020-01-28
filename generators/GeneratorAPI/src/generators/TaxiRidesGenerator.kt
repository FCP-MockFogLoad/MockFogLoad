package com.fcp.generators

import com.amazonaws.services.s3.AmazonS3
import com.fcp.ApplicationConfig
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class TaxiRides(val rideId: Long,
                     val isStart : String,
                     val startTime : String,
                     val endTime : String,
                     val startLongitude : Float,
                     val startLatitude : Float,
                     val endLongitude : Float,
                     val endLatitude : Float,
                     val passengerCount : Short,
                     val taxiId : Long,
                     val driverId : Long,
                     override val date: LocalDateTime) : IGeneratorValue{

    override val unit: String
        get() =  ""

    override val value: Float
        get() = startLongitude
}

class TaxiRidesGenerator(app: ApplicationConfig, seed: Long, bucketName: String): Generator<TaxiRides>("TaxiRides", app, seed) {

    init {
        if (taxiRides == null) {
            initializeTaxiRidesData(bucketName)
        }
    }

    companion object {
        private var taxiRides: List<TaxiRides>? = null

        init {
            registerGeneratorType(
                "TaxiRides",
                TaxiRidesGenerator::class
            )
        }

        private fun initializeTaxiRidesData(bucketName: String) {
            val resource =
                loadResourceHTTP(bucketName, "taxiRides")
            taxiRides = resource.split("\n")
                .map { line -> try {
                    mapToTaxiRides(line)
                } catch(e: Exception) {
                    TaxiRides(
                        0, "", "", "",
                        0f, 0f, 0f, 0f,
                        0, 0, 0, LocalDateTime.now()
                    )
                } }.toList()
        }

        private fun mapToTaxiRides(line: String): TaxiRides {
            var result: List<String> = line.split(",").map { it.trim() }
            return TaxiRides(
                result.get(0).toLong(),
                result.get(1),
                result.get(2),
                result.get(3),
                result.get(4).toFloatOrNull() ?: 0.0F,
                result.get(5).toFloatOrNull() ?: 0.0F,
                result.get(6).toFloatOrNull() ?: 0.0F,
                result.get(7).toFloatOrNull() ?: 0.0F,
                result.get(8).toShort(),
                result.get(9).toLong(),
                result.get(10).toLong(),
                LocalDateTime.now()
            )
        }

        @Suppress("unused")
        fun uploadResources(s3: AmazonS3, bucketName: String, force: Boolean = false): Boolean {
            return uploadResource(
                s3, bucketName,
                "taxiData/nycTaxiRides_50M", "taxiRides", force
            )
        }
    }

    override fun getRandomValue(date: LocalDateTime): TaxiRides {
        val passedMinutes = ChronoUnit.MINUTES.between(app.startDate, date)
        return taxiRides!![(passedMinutes % taxiRides!!.size).toInt()]
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<TaxiRides> {
        return List(amount) { getRandomValue(date) }
    }

}