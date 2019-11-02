#**Generator API**
=

## Import & Build & Run
Before  building, ensure that Kotlin and Ktor plugins are installed in your Intellij Idea. Also make sure that Intellij is in the latest version  to build succesfully gradle.

```
 Import Project -> Select Directory -> Import Project from External Model -> Gradle -> Finish
```
The building and indexing will begin automatically from Intellij.

When the building is done succesfully then you can
run the **Application.kt** 

The server is set on and ready to be used from 
http://localhost:8080

## Temperature Generator
To send Get Requests to server and respond with a json with temperature data, postman used in our case.

There are three cases to get succesfully random Temperatures from the generator.

The below will return one random temperature!
```
http://localhost:8080/temperature/random/
```

Next use is:
```
http://localhost:8080/temperature/random/{amount}
```
{amount} is the number of random data generated and be defined as requested.

Another option is to return the temperature of the day per hour.
```
http://localhost:8080/temperature/random/day
```
