package com.fcp.generators.power

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue

data class Power(val kW: Float,
                 val voltage: Float,
                 val amp: Int,
                 val kmh: Int) : IGeneratorValue {
    override val unit: String
        get() =  "kW"

    override val value: Float
        get() = kW
}

class PowerGenerator: Generator<Power>("Power") {
    override fun getRandomValue(): Power {
        return Power(getKiloWatts(), getVoltageFromPercentage(), getAmp(),getKilometresPerHour())
    }

    override fun generateRandomValues(amount: Int): List<Power> {
        return List(amount) { getRandomValue() }
    }

    private fun getKiloWatts() : Float {
        return (getVoltageFromPercentage() * getAmp()) / 1000
    }

    private fun getVoltageFromPercentage() : Float{
        return randomFloat(350f, 550f) * getPercentage()
    }

    private fun getPercentage(): Int{
        return (60 until 100).random()
    }

    private fun getKilometresPerHour(): Int{
        return (40 until 180).random()
    }

    private fun getAmp() : Int{
        return (16 until 32).random()
    }

}