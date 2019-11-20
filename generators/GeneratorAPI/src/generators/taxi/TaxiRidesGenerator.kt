package com.fcp.generators.taxi

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.io.File
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
                     val driverId : Long) : IGeneratorValue{

    override val unit: String
        get() =  ""

    override val value: Float
        get() = startLongitude
}

class TaxiRidesGenerator: Generator<TaxiRides>("TaxiRides"){

    var taxiRides = listOf<TaxiRides>()

    init {
        val file = File("resources/taxiData/nycTaxiRides_50M")
        taxiRides = readFileAndStoreInList(file)
    }

    private fun readFileAndStoreInList(file: File) : List<TaxiRides>
            = file.useLines { it.map { line :String -> mapToTaxiRides(line)}.toList() }

    private fun mapToTaxiRides(line : String) : TaxiRides{
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
            result.get(10).toLong())
    }

    override fun getRandomValue(): TaxiRides {
        return taxiRides.random()
    }

}