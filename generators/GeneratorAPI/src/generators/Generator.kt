package com.fcp.generators

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.fcp.ApplicationConfig
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.createType


data class GeneratorConfig(val type: String, val amount: Int) {

}

interface IGeneratorValue {
    /** The date and time of this datapoint. */
    val date: LocalDateTime

    /** The timestamp of this datapoint. */
    val timestamp: Long
        get() {
            return date.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        }

    /** The unit of the generated values. */
    val unit: String

    /** The floating-point value; exact meaning is dependent on the generator. */
    val value: Float
}

abstract class BaseGenerator(val type: String, val app: ApplicationConfig, seed: Long) {
    /** A seeded random number generator. */
    protected val random: Random = Random(seed)

    /** Return a random value of the generated type, mainly used for testing. */
    abstract fun generateValue(date: LocalDateTime): IGeneratorValue

    companion object {
        /** Upload a resource to Amazon S3. */
        fun uploadResource(
            s3: AmazonS3, bucketName: String,
            resourcePath: String, key: String,
            forceOverride: Boolean = false
        ): Boolean {
            if (s3.doesObjectExist(bucketName, key) && !forceOverride) {
                println("not uploading $key because it already exists")
                return false
            }

            val resource = this::class.java.classLoader.getResource(resourcePath).readText()
            println("uploading $key...")

            try {
                s3.putObject(bucketName, key, resource)
                s3.setObjectAcl(bucketName, key, CannedAccessControlList.PublicRead);
                println("done!")
            } catch (e: AmazonServiceException) {
                println(e.message)
                return true
            }

            return false
        }

        /** Load a resource from Amazon S3. */
        fun loadResource(s3: AmazonS3, bucketName: String, key: String): String {
            val resource = s3.getObject(bucketName, key)
            val sc = Scanner(resource.objectContent)
            val sb = StringBuffer()
            while (sc.hasNext()) {
                sb.append(sc.nextLine())
                sb.append("\n")
            }

            return sb.toString()
        }

        fun loadResourceHTTP(bucketName: String, key: String): String {
            var url = URL("https://$bucketName.s3.amazonaws.com/$key")
            val inputStream = BufferedInputStream(url.openStream())
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            var n = 0

            while (-1 != inputStream.read(buf).also { n = it }) {
                out.write(buf, 0, n)
            }

            out.close()
            inputStream.close()

            return out.toString()
        }

        /** A dictionary of registered generator types, used for reflection. */
        val registeredGenerators = HashMap<String, KClass<*>>()

        /** Register a new generator type. */
        fun registerGeneratorType(name: String, class_: KClass<*>) {
            if (registeredGenerators.containsKey(name)) {
                println("duplicate generator type '$name'")
                return
            }

            println("registering generator type '$name'...")
            registeredGenerators[name] = class_
        }

        /** Create a generator by name. */
        fun spawnGenerator(name: String, app: ApplicationConfig, seed: Long, bucketName: String): BaseGenerator {
            // Force class to load
            Class.forName("com.fcp.generators.${name}Generator")

            if (!registeredGenerators.containsKey(name)) {
                throw Exception("unknown generator type '$name'")
            }

            val class_ = registeredGenerators[name]!!
            val appConfigType = ApplicationConfig::class.createType()
            val longType = Long::class.createType()
            val stringType = String::class.createType()

            for (ctor in class_.constructors) {
                if (ctor.parameters.size != 3)
                    continue

                if (ctor.parameters[0].type != appConfigType)
                    continue

                if (ctor.parameters[1].type != longType)
                    continue

                if (ctor.parameters[2].type != stringType)
                    continue

                return ctor.call(app, seed, bucketName) as BaseGenerator
            }

            throw Exception("generator '$name' is missing constructor!")
        }
    }
}

abstract class Generator<T: IGeneratorValue>(type: String, app: ApplicationConfig, seed: Long): BaseGenerator(type, app, seed) {
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
        return from + (abs(from - to) * random.nextFloat());
    }
}