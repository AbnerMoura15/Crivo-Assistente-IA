package com.ragbot.adapter.out.parser;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.ragbot.application.port.out.DocumentParserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class CsvParserAdapter implements DocumentParserPort {

    private static final Logger log = LoggerFactory.getLogger(CsvParserAdapter.class);

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (mimeType.equals("text/csv") || mimeType.equals("application/csv"));
    }

    @Override
    public String extractText(InputStream input, String fileName) {
        StringBuilder sb = new StringBuilder();
        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext();
            String[] line;
            int row = 0;

            while ((line = reader.readNext()) != null) {
                row++;
                sb.append("Registro ").append(row).append(": ");
                if (headers != null) {
                    for (int i = 0; i < line.length; i++) {
                        String header = i < headers.length ? headers[i] : "coluna" + i;
                        sb.append(header).append("=").append(line[i]);
                        if (i < line.length - 1) sb.append(", ");
                    }
                } else {
                    sb.append(String.join(" | ", line));
                }
                sb.append("\n");
            }
            log.info("CSV '{}' extraído: {} registros", fileName, row);
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Erro ao processar CSV: " + fileName, e);
        }
        return sb.toString();
    }
}
