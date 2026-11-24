FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /app
COPY pom.xml .
COPY certs certs
COPY src src
RUN set -eux; \
    cert_dir="/app/certs"; \
    cert_files="$(find "$cert_dir" -maxdepth 1 -type f \( -name '*.crt' -o -name '*.cer' -o -name '*.pem' \) 2>/dev/null || true)"; \
    if [ -n "$cert_files" ]; then \
      echo "Installing extra CA certificates from $cert_dir"; \
      mkdir -p /usr/local/share/ca-certificates/extra; \
      for f in $cert_files; do cp "$f" /usr/local/share/ca-certificates/extra/; done; \
      if command -v update-ca-certificates >/dev/null 2>&1; then update-ca-certificates; fi; \
      if command -v keytool >/dev/null 2>&1; then \
        for f in $cert_files; do \
          alias="extra-$(basename "$f" | tr '. ' '__' | tr -cd 'A-Za-z0-9_-')"; \
          keytool -importcert -noprompt -trustcacerts -storepass changeit -keystore "$JAVA_HOME/lib/security/cacerts" -alias "$alias" -file "$f"; \
        done; \
      fi; \
    fi
RUN mvn -B -ntp package
FROM eclipse-temurin:21-jre@sha256:18bb4a91a3dea3f03ee31d3755af34dfb40f904fc77ab56cdc2f092e32fff9c2
WORKDIR /app
COPY --from=build /app/target/oidf-demo-0.1.0.jar app.jar
COPY trustor /usr/local/bin/trustor
RUN chmod +x /usr/local/bin/trustor
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
