cd kafka_2.12-2.3.0

bin/zookeeper-server-start.sh config/zookeeper.properties &
zookeeper_id=$!
bin/kafka-server-start.sh config/server.properties &
kafka_id=$!

bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1 --topic topic

cd ../exampleapp

mvn clean package -P add-dependencies-for-IDEA
java -jar target/exampleapp-1.0-SNAPSHOT.jar

kill $kafka_id
wait $kafka_id
kill $zookeeper_id