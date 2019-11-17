package com.fcp.generators.taxi

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.io.File
import kotlin.collections.listOf

data class TaxiFares(val rideId: Long,
                     val taxiId : Long,
                     val driverId : Long,
                     val startTime : String,
                     val paymentType : String,
                     val tip : Float,
                     val tolls : Float,
                     val totalFare : Float) : IGeneratorValue{

    override val unit: String
        get() =  "$"

    override val value: Float
        get() = totalFare
}

class TaxiFaresGenerator: Generator<TaxiFares>("TaxiFares"){

    var taxiFares = listOf<TaxiFares>()

    init {
        val file = File("resources/taxiData/nycTaxiFares_50M")
        taxiFares = readFileAndStoreInList(file)
    }

    private fun readFileAndStoreInList(file: File) : List<TaxiFares>
       = file.useLines { it.map { line :String -> mapToTaxiFares(line)}.toList() }

    private fun mapToTaxiFares(line : String) : TaxiFares{
        var result: List<String> = line.split(",").map { it.trim() }
        return TaxiFares(
            result.get(0).toLong(),
            result.get(1).toLong(),
            result.get(2).toLong(),
            result.get(3),
            result.get(4),
            result.get(5).toFloat(),
            result.get(6).toFloat(),
            result.get(7).toFloat())
    }

    override fun getRandomValue(): TaxiFares {
        return taxiFares.random()
    }

}