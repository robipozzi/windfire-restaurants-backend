FROM openjdk:17
LABEL maintainer="Roberto Pozzi <r.robipozzi@gmail.com>"
LABEL version="1.0"
LABEL description="Spring Boot Windfire restaurants backend"
COPY target/windfire-restaurants-backend-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]