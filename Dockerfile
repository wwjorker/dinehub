FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /workspace/backend
COPY backend/pom.xml backend/mvnw backend/mvnw.cmd ./
COPY backend/.mvn/ .mvn/
COPY backend/dinehub-common/pom.xml dinehub-common/pom.xml
COPY backend/dinehub-pojo/pom.xml dinehub-pojo/pom.xml
COPY backend/dinehub-server/pom.xml dinehub-server/pom.xml
RUN mvn -B -DskipTests dependency:go-offline
COPY backend/ ./
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S dinehub && adduser -S dinehub -G dinehub
WORKDIR /app
COPY --from=build /workspace/backend/dinehub-server/target/dinehub-server-1.0-SNAPSHOT.jar app.jar
USER dinehub
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
