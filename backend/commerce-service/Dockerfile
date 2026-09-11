FROM amazoncorretto:21

ARG JAR_FILE=commerce-api/build/libs/commerce-api-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} commerce-service.jar

ENTRYPOINT ["java","-jar","/commerce-service.jar"]
