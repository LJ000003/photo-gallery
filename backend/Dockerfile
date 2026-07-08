FROM eclipse-temurin:17-jre-alpine

# 使用中科大 Alpine 镜像源，避免连接海外源过慢
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories && \
    apk add --no-cache font-wqy-zenhei

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
