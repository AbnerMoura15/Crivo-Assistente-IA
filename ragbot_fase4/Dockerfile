# ── Estágio 1: Build ────────────────────────────────────────────────────────
# Usa imagem completa do Maven para compilar e rodar os testes
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copia o pom.xml primeiro para aproveitar o cache de camadas do Docker
# Se o pom.xml não mudar, o Maven não rebaixa as dependências
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Baixa dependências (camada cacheada enquanto pom.xml não mudar)
RUN ./mvnw dependency:go-offline -q

# Copia o código-fonte e compila
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Extrai as camadas do JAR para otimizar o rebuild (Spring Boot Layertools)
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Estágio 2: Runtime ───────────────────────────────────────────────────────
# Imagem mínima — apenas JRE, sem Maven, sem código-fonte
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuário não-root por segurança
RUN addgroup -S ragbot && adduser -S ragbot -G ragbot

WORKDIR /app

# Copia as camadas do Spring Boot na ordem correta
# As camadas menos voláteis ficam no início (melhor cache)
COPY --from=builder /build/target/extracted/dependencies/          ./
COPY --from=builder /build/target/extracted/spring-boot-loader/    ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/           ./

# Diretório para os documentos enviados pelos usuários
RUN mkdir -p /data/documents && chown -R ragbot:ragbot /data /app

USER ragbot

# Porta da aplicação
EXPOSE 8080

# Health check interno do container
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Entrypoint com configurações de memória adequadas para produção
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
