package com.fcp.temperature

import com.fcp.generators.Generator

class TemperatureGenerator: Generator<Temperature>() {
    override fun getRandomValue(): Temperature {
        return Temperature(randomFloat(-30f, 50f), randomFloat(0f, 100f),
                           randomFloat(1000f, 1100f));
    }
}