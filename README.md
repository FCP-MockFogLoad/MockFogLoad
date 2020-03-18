# MockFogLoad

This readme details the requirements and steps necessary to build the generators and orchestrator components of MockFogLoad.  Instructions on how to install and run MockFogLight can be found in its [repository](https://github.com/OpenFogStack/MockFogLight) while documentation on how to configure the orchestrator and the generators can be found on [ReadTheDocs](https://mockfogload.readthedocs.io/en/latest/).
### Requirements
To build either the generator or the orchestrator gradle is required:

Install gradle
```
wget https://services.gradle.org/distributions/gradle-5.2.1-bin.zip -P /tmp
```
Configure PATH environment for gradle
```
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
sudo touch /etc/profile.d/gradle.sh
export GRADLE_HOME=/opt/gradle/gradle-5.2.1
export PATH=${GRADLE_HOME}/bin:${PATH}
source /etc/profile.d/gradle.sh
```

### Build Jars
After requirements are installed, you can build and run the respective jar:

For the generators:
```
cd ~/MockFogLoad/generators/GeneratorAPI
./gradlew build
```
Optional: run the generator application
```
java -server -jar build/libs/generators-0.0.1.jar
```

For the orchestrator:
```
cd ~/MockFogLoad/orchestrator
./gradlew build
```
Optional: run the orchestrator application
```
java -server -jar build/libs/orchestrator.jar
```

### Build the documentation
In case you want to build the included documentation locally, sphinx is required:

```
pip install sphinx
```

After sphinx is installed, you can create the html versions of the docs by executing:

```
cd ~/docs
make html
```