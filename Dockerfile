FROM openjdk:13-alpine

RUN addgroup -S ids && adduser -S -g ids ids
WORKDIR /home/app/
RUN chown -R ids: ./ && chmod -R u+w ./
RUN mkdir -p /ids/repo/ && chown -R ids: /ids/repo/ && chmod -R u+w /ids/repo/
COPY /target/public-data-space-zenodo-adapter-1.1.0-fat.jar .
EXPOSE 8080
USER ids
ENTRYPOINT ["java", "-jar", "./public-data-space-zenodo-adapter-1.1.0-fat.jar"]