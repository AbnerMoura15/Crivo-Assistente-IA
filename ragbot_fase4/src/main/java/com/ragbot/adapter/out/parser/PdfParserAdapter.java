package com.ragbot.adapter.out.parser;

import com.ragbot.application.port.out.DocumentParserPort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfParserAdapter implements DocumentParserPort {

    private static final Logger log = LoggerFactory.getLogger(PdfParserAdapter.class);

    @Override
    public boolean supports(String mimeType) {
        return "application/pdf".equals(mimeType);
    }

    @Override
    public String extractText(InputStream input, String fileName) {
        try (PDDocument pdf = Loader.loadPDF(input.readAllBytes())) {
            String text = new PDFTextStripper().getText(pdf);
            log.info("PDF '{}' extraido: {} paginas", fileName, pdf.getNumberOfPages());
            return text;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao processar PDF: " + fileName, e);
        }
    }
}
