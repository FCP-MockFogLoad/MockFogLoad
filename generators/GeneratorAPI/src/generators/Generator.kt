package com.fcp.generators

import kotlin.math.abs
import kotlin.random.Random

abstract class Generator<T> {
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