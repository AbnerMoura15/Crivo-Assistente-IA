# ── Estágio 1: Build ────────────────────────────────────────────────────────
# maven:3.9-eclipse-temurin-21-alpine já inclui Maven + JDK 21 — sem precisar do mvnw
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copia o pom.xml primeiro para aproveitar o cache de camadas do Docker
# Se o pom.xml não mudar, o Maven não rebaixa as dependências
COPY pom.xml .

# Baixa dependências (camada cacheada enquanto pom.xml não mudar)
RUN mvn dependency:go-offline -q

# Copia o código-fonte e compila
COPY src/ src/
RUN mvn package -DskipTests -q

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

# Variável de ambiente para ativar perfil de produção por padrão
ENV SPRING_PROFILES_ACTIVE=prod

# Entrypoint otimizado para containers com 512 MB de RAM.
# JAVA_OPTS permite sobrescrever/adicionar flags via env var no Railway.
#
# -XX:+UseSerialGC        GC de thread única — menor overhead que G1GC em heaps < 350 MB
# -XX:MaxRAMPercentage    60% do limite do container (~307 MB em 512 MB)
# -XX:InitialRAMPercentage 40% — evita crescimento tardio do heap e coletas desnecessárias
# -Xss256k                pilha por thread reduzida de 1 MB para 256 KB
# -XX:+ExitOnOutOfMemoryError reinicia o container limpo em vez de travar com OOM
ENTRYPOINT ["sh", "-c", \
    "exec java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=60.0 \
    -XX:InitialRAMPercentage=40.0 \
    -XX:+UseSerialGC \
    -Xss256k \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom \
    ${JAVA_OPTS:-} \
    org.springframework.boot.loader.launch.JarLauncher"]
