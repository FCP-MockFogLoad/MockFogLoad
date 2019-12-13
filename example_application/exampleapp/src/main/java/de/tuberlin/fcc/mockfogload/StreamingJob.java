/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.tuberlin.fcc.mockfogload

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonParser
import org.apache.flink.shaded.jackson2.org.yaml.snakeyaml.Yaml
import org.apache.flink.shaded.jackson2.org.yaml.snakeyaml.constructor.Constructor
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.slf4j.LoggerFactory

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Arrays
import java.util.Properties
import spark.Spark.post

class StreamingJob {

    class MeasurementType {
        var name: String
        var lowerThreshold: Double = 0.toDouble()
        var upperThreshold: Double = 0.toDouble()
    }

    class Config {
        var email: String
        var measurementTypes: List<MeasurementType>
    }

    companion object {

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val yaml = Yaml(Constructor(Config::class.java!!))
            val inputStream = FileInputStream(File("config.yaml"))
            val config = yaml.load(inputStream) as Config

            post("/") { request, response ->
                println("Hello World!")
                null
            }

            /*
		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		LoggerFactory.getILoggerFactory();

		Properties properties = new Properties();
		properties.setProperty("bootstrap.servers", "localhost:9092");
		properties.setProperty("group.id", "test");
		DataStream<String> stream = env
				.addSource(new FlinkKafkaConsumer<>("topic", new SimpleStringSchema(), properties));
		*/

            /*
		DataStream<String> stream = env
			.socketTextStream("localhost", 9999, ",");

		// Complex streaming job

		stream.print();

		// execute program
		env.execute("Example Application");
		*/
        }
    }
}
