FROM gradle:8.2-jdk11 AS build
COPY ./ .
RUN gradle clean build dockerPrepare

FROM adoptopenjdk/openjdk11:alpine
WORKDIR /home
COPY --from=build /home/gradle/build/docker .
ENTRYPOINT ["/home/service/bin/service"]
