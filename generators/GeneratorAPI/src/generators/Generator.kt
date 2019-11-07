package com.fcp.generators

import kotlin.math.abs
import kotlin.random.Random

data class GeneratorConfig(val type: String, val amount: Int) {

}

interface IGeneratorValue {
    /** The unit of the generated values. */
    val unit: String

    /** The floating-point value; exact meaning is dependent on the generator. */
    val value: Float
}

abstract class BaseGenerator(val type: String)  {
    /** Return a random value of the generated type, mainly used for testing. */
    abstract fun generateValue(): IGeneratorValue
}

abstract class Generator<T: IGeneratorValue>(type: String): BaseGenerator(type) {
    override fun generateValue(): IGeneratorValue {
        return getRandomValue()
    }

    /** Return a random value of the generated type, mainly used for testing. */
    abstract fun getRandomValue(): T

    /** Generate a specified amount of random values. */
    open fun generateRandomValues(amount: Int): List<T> {
        return List(amount) { getRandomValue() };
    }

    /** Helper function for generating floats within an interval. */
    protected fun randomFloat(from: Float, to: Float): Float {
        return from + (abs(from - to) * Random.nextFloat());
    }
}