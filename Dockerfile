FROM openjdk:17-alpine
RUN apk update
# Install tesseract library
RUN apk add --no-cache tesseract-ocr
RUN tesseract -v 
EXPOSE 8080
COPY tessdata /opt/micr-ocr-scanner-api/tessdata
COPY target/micr-ocr-scanner-api*.jar /opt/micr-ocr-scanner-api/app.jar
WORKDIR /opt/micr-ocr-scanner-api/
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar app.jar" ]
