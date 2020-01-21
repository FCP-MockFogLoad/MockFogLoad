package com.fcp.generators

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.fcp.ApplicationConfig
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.LocalDateTime
import java.util.*
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