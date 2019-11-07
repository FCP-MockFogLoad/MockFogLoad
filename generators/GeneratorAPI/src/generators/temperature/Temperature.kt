package com.fcp.temperature

import com.fcp.generators.IGeneratorValue

data class Temperature(val hour: Int,
                       val celsius: Float,
                       val humidity: Float,
                       val pressure: Float) : IGeneratorValue {
    override val unit: String
        get() = "°C"
    override val value: Float
        get() = celsius
}