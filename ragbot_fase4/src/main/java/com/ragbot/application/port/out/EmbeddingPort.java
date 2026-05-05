package com.ragbot.application.port.out;

import java.util.List;

public interface EmbeddingPort {
    List<Float> embed(String text);
}
