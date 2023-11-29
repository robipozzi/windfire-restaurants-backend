# Windfire Restaurants application backend

### Table of Contents
- [Introduction](#introduction)
- [Requirements](#requirements)
- [Run](#run)
    
### Introduction
This repository holds the code for Windfire Restaurants application backend services.


### Requirements
* [Maven](https://maven.apache.org/install.html)
* Java 8: Any compliant JVM should work.
  * [Java 8 JDK from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
  * [Java 8 JDK from IBM (AIX, Linux, z/OS, IBM i)](http://www.ibm.com/developerworks/java/jdk/)


### Run

To build and run the com.rpozzi.windfire.application:
1. `mvn install`
2. `java -jar ./target/[microclimate-project-name]-1.0-SNAPSHOT.jar`

To run the com.rpozzi.windfire.application in Docker use the Docker file called `Dockerfile`. If you do not want to install Maven locally you can use `Dockerfile-tools` to build a container with Maven installed.