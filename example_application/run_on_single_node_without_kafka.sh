cd ./exampleapp

mvn clean package -P add-dependencies-for-IDEA
java -jar target/exampleapp-1.0-SNAPSHOT.jar