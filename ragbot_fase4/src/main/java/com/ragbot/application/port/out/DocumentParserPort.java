package com.ragbot.application.port.out;

import java.io.InputStream;

public interface DocumentParserPort {
    boolean supports(String mimeType);
    String extractText(InputStream input, String fileName);
}
