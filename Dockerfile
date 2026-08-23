FROM eclipse-temurin:21-jdk

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY common/build.gradle.kts common/build.gradle.kts
COPY limbo/build.gradle.kts limbo/build.gradle.kts
COPY velocity/build.gradle.kts velocity/build.gradle.kts
RUN chmod +x gradlew && ./gradlew --no-daemon help

COPY . .
RUN chmod +x gradlew scripts/build-all.sh

CMD ["./scripts/build-all.sh"]
