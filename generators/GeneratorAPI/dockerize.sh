# build the project
./gradlew build

# build the docker container
docker build -t fcp-generators .

# uncomment to run the image after building
docker run -m512M --cpus 2 -it -p 8080:8080 --rm fcp-generators
