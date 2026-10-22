package com.ragbot.domain.exception;

/**
 * Lançada quando o usuário esgotou o limite mensal de tokens.
 * Resulta em HTTP 429 Too Many Requests.
 */
public class TokenLimitExceededException extends RuntimeException {

    private final long used;
    private final long limit;

    public TokenLimitExceededException(String username, long used, long limit) {
        super(String.format(
                "Limite mensal de tokens atingido para '%s': %d/%d tokens usados",
                username, used, limit));
        this.used = used;
        this.limit = limit;
    }

    public long getUsed()  { return used; }
    public long getLimit() { return limit; }
}
