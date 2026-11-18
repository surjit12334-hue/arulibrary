# ---------- build stage: compile with the bundled JDBC driver ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY lib/postgresql-42.7.4.jar lib/
COPY src/main/java src/main/java

RUN javac -encoding UTF-8 -cp "lib/*" -d out \
    $(find src/main/java -name '*.java' | sort)

# ---------- runtime stage: JRE + compiled classes + static assets ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/out out
COPY lib lib
COPY web web
COPY sql sql

ENV WEB_HOST=0.0.0.0
EXPOSE 8080

CMD ["java", "-cp", "out:lib/*", "com.library.Main", "--web"]
