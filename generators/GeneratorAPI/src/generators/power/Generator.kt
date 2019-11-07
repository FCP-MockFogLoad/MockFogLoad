package com.fcp.generators.power

import com.fcp.generators.Generator
import com.fcp.generators.IGeneratorValue
import kotlin.random.Random

data class Power(val mw: Float) : IGeneratorValue {
    override val unit: String
        get() =  "MW"

    override val value: Float
        get() = mw
}

class PowerGenerator: Generator<Power>("Power") {
    override fun getRandomValue(): Power {
        // TODO
        return Power(randomFloat(0f, 100f))
    }
}