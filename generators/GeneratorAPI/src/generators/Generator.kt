package com.fcp.generators

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.random.Random

data class GeneratorConfig(val type: String, val amount: Int) {

}

interface IGeneratorValue {
    /** The date and time of this datapoint. */
    val date: LocalDateTime

    /** The unit of the generated values. */
    val unit: String

    /** The floating-point value; exact meaning is dependent on the generator. */
    val value: Float
}

abstract class BaseGenerator(val type: String)  {
    /** Return a random value of the generated type, mainly used for testing. */
    abstract fun generateValue(date: LocalDateTime): IGeneratorValue
}

abstract class Generator<T: IGeneratorValue>(type: String): BaseGenerator(type) {
    override fun generateValue(date: LocalDateTime): IGeneratorValue {
        return getRandomValue(date)
    }

    /** Return a random value of the generated type, mainly used for testing. */
    abstract fun getRandomValue(date: LocalDateTime): T

    /** Generate a specified amount of random values. */
    open fun generateRandomValues(date: LocalDateTime, amount: Int): List<T> {
        return List(amount) { getRandomValue(date) }
    }

    /** Helper function for generating floats within an interval. */
    protected fun randomFloat(from: Float, to: Float): Float {
        return from + (abs(from - to) * Random.nextFloat());
    }
}