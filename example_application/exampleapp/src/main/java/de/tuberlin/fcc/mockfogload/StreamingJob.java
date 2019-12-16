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

package de.tuberlin.fcc.mockfogload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.shaded.jackson2.org.yaml.snakeyaml.Yaml;
import org.apache.flink.shaded.jackson2.org.yaml.snakeyaml.constructor.Constructor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import static spark.Spark.post;

public class StreamingJob {
	public StreamingJob() {
	}

	public static class MeasurementType{
		String name;
		double lowerThreshold;
		double upperThreshold;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public double getLowerThreshold() {
			return lowerThreshold;
		}

		public void setLowerThreshold(double lowerThreshold) {
			this.lowerThreshold = lowerThreshold;
		}

		public double getUpperThreshold() {
			return upperThreshold;
		}

		public void setUpperThreshold(double upperThreshold) {
			this.upperThreshold = upperThreshold;
		}
	}

	public static class Config{
		String email;
		List<MeasurementType> measurementTypes;

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public List<MeasurementType> getMeasurementTypes() {
			return measurementTypes;
		}

		public void setMeasurementTypes(List<MeasurementType> measurementTypes) {
			this.measurementTypes = measurementTypes;
		}
	}

	public static void main(String[] args) throws Exception {
		Yaml yaml = new Yaml(new Constructor(Config.class));
		InputStream inputStream = new FileInputStream(new File("config.yaml"));
		Config config = (Config) yaml.load(inputStream);
		Map<String, MeasurementType> measurementTypeMap = new HashMap<>();

		for (MeasurementType m:config.measurementTypes){
			measurementTypeMap.put(m.name, m);
		}

		MeasurementType heartRateConfig = measurementTypeMap.get("heartRate");

		post("/", (request, response) -> {
			try {
				JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();

				for (MeasurementType type:config.measurementTypes){
					JsonElement measurement = jsonObject.get(type.name);
					if (measurement == null){
						continue;
					}

					double value = measurement.getAsDouble();

					if (value < type.lowerThreshold){
						System.out.println("Measurement for "+type.name+" violated lower threshold: "+value +" < "+type.lowerThreshold);
					}

					if (value > type.upperThreshold){
						System.out.println("Measurement for "+type.name+" violated upper threshold: "+value +" > "+type.upperThreshold);
					}
				}

			}catch (Exception e){
				response.status(400);
				return e;
			}

			response.status(200);
			return "OK";
		});

		/*
		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		LoggerFactory.getILoggerFactory();

		Properties properties = new Properties();
		properties.setProperty("bootstrap.servers", "localhost:9092");
		properties.setProperty("group.id", "test");
		DataStream<String> stream = env"
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
