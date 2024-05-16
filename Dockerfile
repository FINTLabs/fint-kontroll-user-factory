FROM gradle:7.6-jdk17 as builder
USER root
COPY . .
RUN gradle --no-daemon build

FROM gcr.io/distroless/java21
ENV JAVA_TOOL_OPTIONS -XX:+ExitOnOutOfMemoryError
COPY --from=builder /home/gradle/build/libs/fint-kontroll-user-factory-*.jar /data/app.jar
CMD ["/data/app.jar"]

