package com.ragbot.adapter.out.config;

import com.ragbot.application.port.out.RagConfigPort;
import com.ragbot.domain.entity.RagConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter de persistência de configuração do RAG (#25).
 *
 * Armazena cada campo como linha na tabela rag_config (chave/valor).
 * Em caso de leitura com chave ausente, usa os defaults do domínio.
 */
@Component
public class PgRagConfigAdapter implements RagConfigPort {

    private static final Logger log = LoggerFactory.getLogger(PgRagConfigAdapter.class);

    private final JdbcTemplate jdbc;

    public PgRagConfigAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RagConfig load() {
        Map<String, String> kv = new HashMap<>();
        try {
            jdbc.query("SELECT key, value FROM rag_config",
                    (rs, rowNum) -> {
                        kv.put(rs.getString("key"), rs.getString("value"));
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Não foi possível carregar rag_config, usando defaults: {}", e.getMessage());
            return RagConfig.defaults();
        }

        RagConfig def = RagConfig.defaults();
        return new RagConfig(
                intVal(kv, "retrieval.top-k",                  def.topK()),
                dblVal(kv, "retrieval.min-score",              def.minScore()),
                intVal(kv, "retrieval.short-chunk-length",     def.shortChunkLength()),
                boolVal(kv,"retrieval.hybrid.enabled",         def.hybridEnabled()),
                dblVal(kv, "retrieval.hybrid.semantic-weight", def.semanticWeight()),
                longVal(kv,"tokens.monthly-limit",             def.monthlyTokenLimit()),
                intVal(kv, "chat.max-history-messages",        def.maxHistoryMessages())
        );
    }

    @Override
    public void save(RagConfig c) {
        upsert("retrieval.top-k",                  String.valueOf(c.topK()));
        upsert("retrieval.min-score",              String.valueOf(c.minScore()));
        upsert("retrieval.short-chunk-length",     String.valueOf(c.shortChunkLength()));
        upsert("retrieval.hybrid.enabled",         String.valueOf(c.hybridEnabled()));
        upsert("retrieval.hybrid.semantic-weight", String.valueOf(c.semanticWeight()));
        upsert("tokens.monthly-limit",             String.valueOf(c.monthlyTokenLimit()));
        upsert("chat.max-history-messages",        String.valueOf(c.maxHistoryMessages()));
        log.info("RagConfig salvo: topK={} minScore={} hybrid={} semWeight={} limit={} maxHist={}",
                c.topK(), c.minScore(), c.hybridEnabled(), c.semanticWeight(),
                c.monthlyTokenLimit(), c.maxHistoryMessages());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void upsert(String key, String value) {
        jdbc.update("""
                INSERT INTO rag_config (key, value, updated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (key) DO UPDATE
                    SET value = EXCLUDED.value, updated_at = NOW()
                """, key, value);
    }

    private static int     intVal (Map<String,String> m, String k, int    d){ try{ return Integer.parseInt(m.getOrDefault(k, String.valueOf(d))); } catch(Exception e){ return d; } }
    private static double  dblVal (Map<String,String> m, String k, double d){ try{ return Double.parseDouble(m.getOrDefault(k, String.valueOf(d))); } catch(Exception e){ return d; } }
    private static long    longVal(Map<String,String> m, String k, long   d){ try{ return Long.parseLong(m.getOrDefault(k, String.valueOf(d)));    } catch(Exception e){ return d; } }
    private static boolean boolVal(Map<String,String> m, String k, boolean d){ return Boolean.parseBoolean(m.getOrDefault(k, String.valueOf(d))); }
}
