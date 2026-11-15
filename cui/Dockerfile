FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN curl -fsSL https://registry.npmjs.org/swagger-ui-dist/-/swagger-ui-dist-5.34.1.tgz -o /tmp/swagger.tgz \
 && echo '552afc7d77c8f77a1b61a42270b25b2d42864710e88e3fd27d12430cb0a2a064d4063e012bf363e1e13d8c278202727154b8bb892fb4f0f6e264bcab40a06425  /tmp/swagger.tgz' | sha512sum -c - \
 && mkdir -p src/main/resources/swagger \
 && tar -xzf /tmp/swagger.tgz --strip-components=1 -C src/main/resources/swagger
RUN mvn -B -ntp package
FROM eclipse-temurin:21-jre@sha256:18bb4a91a3dea3f03ee31d3755af34dfb40f904fc77ab56cdc2f092e32fff9c2
WORKDIR /app
COPY --from=build /app/target/federation-cui-0.1.0.jar app.jar
COPY entity-a /usr/local/bin/entity-a
RUN chmod +x /usr/local/bin/entity-a
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
