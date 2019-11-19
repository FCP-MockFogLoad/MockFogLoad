package com.fcp.generators.power

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import java.time.LocalDateTime

data class Power(override val date: LocalDateTime,
                 val kW: Float,
                 val voltage: Float,
                 val amp: Int,
                 val kmh: Int) : IGeneratorValue {
    override val unit: String
        get() =  "kW"

    override val value: Float
        get() = kW
}

class PowerGenerator: Generator<Power>("Power") {
    override fun getRandomValue(date: LocalDateTime): Power {
        return Power(date, getKiloWatts(), getVoltageFromPercentage(), getAmp(), getKilometresPerHour())
    }

    private fun getKiloWatts() : Float {
        return (getVoltageFromPercentage() * getAmp()) / 1000
    }

    private fun getVoltageFromPercentage() : Float{
        return randomFloat(300f, 550f)
    }
    
    private fun getKilometresPerHour(): Int{
        return (40 until 180).random()
    }

    private fun getAmp() : Int{
        return ((16 until 32).random())
    }

}