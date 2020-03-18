Generators
==========

MockFogLoad provides a single generator app from which different kinds of generators can be spawned.

Generator Types
---------------

All data is generated deterministically based on an increasing, specifiable date value.
Below is a list of the available generator types as well as their configurable paramters and format string options:

* HeartRate: Generates various medical information.
    * Available format string values: age, sex, heartRate, chestPainLevel, bloodPressure, cholestorol, bloodSugar, electroCardiographic
    * Options: None

* Temperature: Generates temperatures according to given location and month.
    * Available format string values: celsius
    * Options:
        * Location: Location for which temperatures should be generated. Can be: BerlinDahlem, Bremen, Frankfurt, Salzburg, Wien
        * Month: Month for which to generate temperatures. Can be: Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec

* Power:
    * Available format string values: activePower, reactivePower, voltage, ampere, kwh
    * Options: None

* TaxiFares:
    * Available format string values: rideId, taxiId, driverId, startTime, paymentType, tip, tolls, totalFare
    * Options: None

* TaxiRides: 
    * Available format string values: rideId, isStart, startTime, endTime, startLongitude, startLatitude, endLongitude, endLatitude, passengerCount, taxiId, driverId
    * Options: None

Implementing A New Generator
----------------------------

Implementing a new generator type involves a few simple changes to the kotlin project.

1. Create a new kotlin `.kt` file in the directory `src/generators`. The full package name of your generator must have the form `com.fcp.generators.{Name}Generator`. In this file, implement a class that inherits from `com.fcp.generators.Generator<T>`, where `T` is the type of your generator's data class. 
2. (Optional) Implement a companion object to fetch your generator's data from S3. The base generator class provides the functions `loadResource` and `uploadResource` to handle down- and uploading from S3, respectively.
3. Implement a static initializer block in your generator containing a call to `BaseGenerator.registerGeneratorType("Name", this::class)` to ensure that instances of your generator can be created.
4. Implement an overriden `fun getRandomValue(date: LocalDateTime): T`. This function handles the actual data generation. Your generated datapoint should be based solely on the passed date value to guarantee deterministic results.
5. That's it! Your generator type can now be created by referencing the name you assigned in a modify event.