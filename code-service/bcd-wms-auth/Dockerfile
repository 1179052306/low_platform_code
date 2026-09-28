FROM eclipse-temurin:8-jre
LABEL module=bcd-wms-auth

WORKDIR /app

# Copy jar
COPY target/bcd-wms-auth.jar app.jar

# Copy external config
COPY target/config/ /app/config/

# Copy dependencies (thin jar only)

# JVM options
ENV JAVA_OPTS="-server -Xms1G -Xmx1G -XX:+UseG1GC -XX:+UseTLAB -Dfile.encoding=utf-8"

EXPOSE 8003

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.config.location=file:/app/config/,classpath:/"]
