package com.ragbot.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço #5 — Rate Limiting por usuário e por IP.
 *
 * Implementa sliding window simples em memória (sem Redis).
 * Adequado para instância única. Para múltiplas instâncias, substituir
 * por Redis com o mesmo contrato (RateLimiterService continua igual —
 * apenas o armazenamento muda, via outro adapter).
 *
 * Chaves:
 *   "user:<username>" → usuário autenticado (limite maior)
 *   "ip:<address>"    → requisição anônima  (limite menor)
 *
 * Os contadores são resetados a cada minuto via @Scheduled.
 * O contador diário é resetado à meia-noite.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final int userPerMinute;
    private final int userPerDay;
    private final int anonPerMinute;
    private final int anonPerDay;

    // Contadores em memória — thread-safe via AtomicInteger
    private final ConcurrentHashMap<String, AtomicInteger> minuteCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> dayCounters    = new ConcurrentHashMap<>();

    public RateLimiterService(
            @Value("${ragbot.rate-limit.user-per-minute:20}") int userPerMinute,
            @Value("${ragbot.rate-limit.user-per-day:500}")   int userPerDay,
            @Value("${ragbot.rate-limit.anon-per-minute:5}")  int anonPerMinute,
            @Value("${ragbot.rate-limit.anon-per-day:50}")    int anonPerDay) {
        this.userPerMinute = userPerMinute;
        this.userPerDay    = userPerDay;
        this.anonPerMinute = anonPerMinute;
        this.anonPerDay    = anonPerDay;
    }

    /**
     * Tenta adquirir uma "ficha" para a chave informada.
     *
     * @param key "user:<username>" ou "ip:<address>"
     * @return true se permitido, false se limite atingido
     */
    public boolean tryAcquire(String key) {
        boolean isAnon = key.startsWith("ip:");
        int limitMin = isAnon ? anonPerMinute : userPerMinute;
        int limitDay = isAnon ? anonPerDay    : userPerDay;

        int minuteCount = minuteCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (minuteCount > limitMin) {
            minuteCounters.get(key).decrementAndGet(); // desfaz o incremento
            log.warn("Rate limit por minuto atingido para: {}", key);
            return false;
        }

        int dayCount = dayCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (dayCount > limitDay) {
            minuteCounters.get(key).decrementAndGet();
            dayCounters.get(key).decrementAndGet();
            log.warn("Rate limit diário atingido para: {}", key);
            return false;
        }

        return true;
    }

    /** Retorna quantas requisições essa chave fez no minuto atual */
    public int getMinuteCount(String key) {
        AtomicInteger counter = minuteCounters.get(key);
        return counter == null ? 0 : counter.get();
    }

    /** Retorna quantas requisições essa chave fez no dia atual */
    public int getDayCount(String key) {
        AtomicInteger counter = dayCounters.get(key);
        return counter == null ? 0 : counter.get();
    }

    /** Reseta contadores por minuto a cada 60 segundos */
    @Scheduled(fixedDelay = 60_000)
    public void resetMinuteCounters() {
        int size = minuteCounters.size();
        minuteCounters.clear();
        if (size > 0) log.debug("Rate limit: {} contadores por minuto resetados", size);
    }

    /** Reseta contadores diários à meia-noite */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDayCounters() {
        int size = dayCounters.size();
        dayCounters.clear();
        log.info("Rate limit: {} contadores diários resetados", size);
    }
}
