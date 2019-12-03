package com.fcp.generators.taxi

import com.amazonaws.services.s3.AmazonS3
import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.io.File
import java.time.LocalDateTime
import kotlin.collections.listOf

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

class TaxiRidesGenerator(s3: AmazonS3, bucketName: String): Generator<TaxiRides>("TaxiRides"){

    init {
        if (taxiRides == null) {
            initializeTaxiRidesData(s3, bucketName)
        }
    }

    companion object {
        private var taxiRides: List<TaxiRides>? = null

        private fun initializeTaxiRidesData(s3: AmazonS3, bucketName: String) {
            val resource = loadResource(s3, bucketName, "taxiRides")
            taxiRides = resource.split("\n")
                .map { line -> try { this.mapToTaxiRides(line) } catch(e: Exception) {
                    TaxiRides(0, "", "", "",
                        0f, 0f, 0f, 0f,
                        0, 0, 0, LocalDateTime.now()) } }.toList()
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
        fun uploadResources(s3: AmazonS3, bucketName: String): Boolean {
            return uploadResource(s3, bucketName,
                "taxiData/nycTaxiRides_50M", "taxiRides")
        }
    }

    override fun getRandomValue(date: LocalDateTime): TaxiRides {
        return taxiRides!!.random()
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<TaxiRides> {
        return List(amount) { getRandomValue(date) }
    }

}