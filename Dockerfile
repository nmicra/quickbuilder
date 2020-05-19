FROM mydocker-repo:8080/docker-virtual/java8
COPY target/*jar-with-dependencies.jar /app.jar
EXPOSE 8080
ENV TZ=Asia/Jerusalem
VOLUME /work
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Xmx256m","-jar","/app.jar","-P:codebasemapping=development=/home/branches/development,master=/home/branches/master"]
HEALTHCHECK --start-period=1m --interval=10s --timeout=3s --retries=3 CMD curl --fail http://localhost:8080 || exit 1

