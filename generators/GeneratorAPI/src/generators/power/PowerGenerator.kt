package com.fcp.generators.power

import com.amazonaws.services.s3.AmazonS3
import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.lang.NumberFormatException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Power(override val date: LocalDateTime,
                 val activePower: Float,
                 val reActivePower: Float,
                 val voltage: Float,
                 val ampere: Float,
                 val Kwh: Float) : IGeneratorValue {
    override val unit: String
        get() =  "kW"

    override val value: Float
        get() = Kwh
}

class PowerGenerator(s3: AmazonS3, bucketName: String): Generator<Power>("Power") {
    init {
        if (powerConsumption == null) {
            initializePowerConsumptionData(s3, bucketName)
        }
    }

    companion object {
        private var powerConsumption: List<Power>? = null

        private fun initializePowerConsumptionData(s3: AmazonS3, bucketName: String) {
            val resource = loadResource(s3, bucketName, "power_consumption")
            powerConsumption = resource.lines().map { line -> this.mapToPowerConsumption(line) }.toList()
        }

        private fun mapToPowerConsumption(line : String): Power {
            val result = line.split(";")
            if (result.size < 9) {
                return Power(LocalDateTime.now(), 0f, 0f, 0f, 0f, 0f)
            }

            return Power(
                LocalDateTime.parse(result.get(0) + " " + result.get(1), DateTimeFormatter.ofPattern("d/M/uuuu HH:mm:ss")),
                try { result.get(2).toFloat() } catch (e: NumberFormatException) { 0.0f },
                try { result.get(3).toFloat() } catch (e: NumberFormatException) { 0.0f },
                try { result.get(4).toFloat() } catch (e: NumberFormatException) { 0.0f },
                try { result.get(5).toFloat() } catch (e: NumberFormatException) { 0.0f },
                try { result.get(6).toFloat() + result.get(7).toFloat() + result.get(8).toFloat() } catch (e: NumberFormatException) { 0.0f })
        }

        @Suppress("unused")
        fun uploadResources(s3: AmazonS3, bucketName: String, forceOverride: Boolean = false): Boolean {
            return uploadResource(s3, bucketName,
                "power/power_consumption.txt", "power_consumption",
                forceOverride)
        }
    }

    override fun getRandomValue(date: LocalDateTime): Power {
        return powerConsumption!!.random()
    }

    override fun generateRandomValues(date: LocalDateTime, amount: Int): List<Power> {
        return List(amount) { getRandomValue(date) }
    }
}