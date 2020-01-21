package com.fcp.generators.taxi

import com.amazonaws.services.s3.AmazonS3
import com.fcp.ApplicationConfig
import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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

class TaxiFaresGenerator(app: ApplicationConfig, seed: Long, bucketName: String): Generator<TaxiFares>("TaxiFares", app, seed) {
    init {
        if (taxiFares == null) {
            initializeTaxiFareData(bucketName)
        }
    }

    companion object {
        private var taxiFares: List<TaxiFares>? = null

        private fun initializeTaxiFareData(bucketName: String) {
            val resource = loadResourceHTTP(bucketName, "taxiFares")
            taxiFares = resource.split("\n").map { line -> try { this.mapToTaxiFares(line) }
            catch (e: Exception) { TaxiFares(0, 0, 0, "", "", 0f, 0f, 0f, LocalDateTime.now()) } }.toList()
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
        fun uploadResources(s3: AmazonS3, bucketName: String, force: Boolean = false): Boolean {
            return uploadResource(s3, bucketName,
                "taxiData/nycTaxiFares_50M", "taxiFares", force)
        }
    }

    override fun getRandomValue(date: LocalDateTime): TaxiFares {
        val passedMinutes = ChronoUnit.MINUTES.between(app.startDate, date)
        return taxiFares!![(passedMinutes % taxiFares!!.size).toInt()]
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<TaxiFares> {
        return List(amount) { getRandomValue(date) }
    }

}