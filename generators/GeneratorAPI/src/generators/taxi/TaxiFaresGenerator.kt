package com.fcp.generators.taxi

import com.amazonaws.services.s3.AmazonS3
import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.time.LocalDateTime

data class TaxiFares(val rideId: Long,
                     val taxiId : Long,
                     val driverId : Long,
                     val startTime : String,
                     val paymentType : String,
                     val tip : Float,
                     val tolls : Float,
                     val totalFare : Float,
                     override val date: LocalDateTime) : IGeneratorValue{

    override val unit: String
        get() =  "$"

    override val value: Float
        get() = totalFare
}

class TaxiFaresGenerator(s3: AmazonS3, bucketName: String): Generator<TaxiFares>("TaxiFares"){
    init {
        if (taxiFares == null) {
            initializeTaxiFareData(s3, bucketName)
        }
    }

    companion object {
        private var taxiFares: List<TaxiFares>? = null

        private fun initializeTaxiFareData(s3: AmazonS3, bucketName: String) {
            val resource = loadResource(s3, bucketName, "taxiFares")
            taxiFares = resource.split("\n").map { line -> this.mapToTaxiFares(line) }.toList()
        }
        private fun mapToTaxiFares(line: String): TaxiFares {
            var result: List<String> = line.split(",").map { it.trim() }
            return TaxiFares(
                result.get(0).toLong(),
                result.get(1).toLong(),
                result.get(2).toLong(),
                result.get(3),
                result.get(4),
                result.get(5).toFloat(),
                result.get(6).toFloat(),
                result.get(7).toFloat(),
                LocalDateTime.now()
            )
        }
        @Suppress("unused")
        fun uploadResources(s3: AmazonS3, bucketName: String): Boolean {
            return uploadResource(s3, bucketName,
                "taxiData/nycTaxiFares_50M", "taxiFares")
        }
    }

    override fun getRandomValue(date: LocalDateTime): TaxiFares {
        return taxiFares!!.random()
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<TaxiFares> {
        return List(amount) { getRandomValue(date) }
    }

}