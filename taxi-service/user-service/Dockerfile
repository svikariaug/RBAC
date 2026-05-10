FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /w
COPY pom.xml .
COPY shared/pom.xml shared/
COPY shared/src shared/src
COPY user-service/pom.xml user-service/
COPY trip-service/pom.xml trip-service/
COPY notification-service/pom.xml notification-service/
COPY user-service/src user-service/src
COPY trip-service/src trip-service/src
COPY notification-service/src notification-service/src
RUN mvn -q -pl user-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /w/user-service/target/user-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
