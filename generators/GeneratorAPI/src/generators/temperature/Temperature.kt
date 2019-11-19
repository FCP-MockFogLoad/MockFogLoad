package com.fcp.temperature

import com.fcp.generators.IGeneratorValue
import java.time.LocalDateTime

data class Temperature(override val date: LocalDateTime, override val value: Float) : IGeneratorValue {
    override val unit: String
        get() = "°C"
}