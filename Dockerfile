FROM openjdk:17.0.2-jdk-bullseye
RUN apt-get update -y && \
    apt-get install -y tesseract-ocr && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
RUN tesseract -v 
EXPOSE 8080
COPY tessdata /opt/micr-ocr-scanner-api/tessdata
COPY target/micr-ocr-scanner-api*.jar /opt/micr-ocr-scanner-api/app.jar
WORKDIR /opt/micr-ocr-scanner-api/
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar app.jar" ]
