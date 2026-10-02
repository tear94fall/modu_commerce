FROM amazoncorretto:21

ARG JAR_FILE=commerce-api/build/libs/commerce-api-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} commerce-service.jar

# exec 형식이라 java 가 PID 1 로 SIGTERM 을 받는다(server.shutdown=graceful). 힙은 컨테이너 메모리 제한의 75%.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/commerce-service.jar"]
