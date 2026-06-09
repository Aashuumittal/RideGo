FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

CMD ["java", "-Dserver.port=${PORT}", "-jar", "target/ridego-0.0.1-SNAPSHOT.jar"]