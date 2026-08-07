FROM eclipse-temurin:17-jre-alpine

# 使用中科大 Alpine 镜像源，避免连接海外源过慢
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories && \
    apk add --no-cache font-wqy-zenhei curl tzdata

# 时区（alpine JVM 缺 tzdata 时无 /etc/localtime → 日志/DB 时间戳整体偏移 8h；两者缺一不可）
ENV TZ=Asia/Shanghai

# 非 root 运行（最小权限）：上传/日志/备份卷需一次性 chown 到 10001（见部署步骤）
RUN adduser -D -u 10001 app

WORKDIR /app
COPY target/*.jar app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx448m", "-XX:MaxMetaspaceSize=128m", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
