package com.ragbot.adapter.in.rest;

import com.ragbot.domain.exception.UnsupportedDocumentTypeException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Valida o upload na borda, antes de gravar em disco ou chamar o parser.
 *
 * - Extensão em lista branca (.pdf, .csv); o MIME vem da extensão, nunca do Content-Type do cliente.
 * - Tipo real pelo conteúdo: PDF começa com "%PDF-"; CSV é texto (sem byte NUL no início).
 * - Nome sanitizado: sem diretório, sem caracteres de controle, até 255 caracteres.
 * O limite de tamanho fica no multipart do Spring (spring.servlet.multipart.max-file-size).
 */
final class UploadValidator {

    static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf",
            "csv", "text/csv");

    private static final int SNIFF_BYTES = 8192;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    record ValidatedUpload(String fileName, String mimeType, InputStream content) {}

    private UploadValidator() {}

    static ValidatedUpload validate(String originalName, long sizeBytes, InputStream raw) throws IOException {
        String fileName = sanitizeFileName(originalName);
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mimeType = ALLOWED.get(ext);
        if (mimeType == null) {
            throw new UnsupportedDocumentTypeException("extensão não permitida (use .pdf ou .csv)");
        }
        if (sizeBytes <= 0) {
            throw new UnsupportedDocumentTypeException("arquivo vazio");
        }

        BufferedInputStream in = new BufferedInputStream(raw, SNIFF_BYTES);
        in.mark(SNIFF_BYTES);
        byte[] head = in.readNBytes(SNIFF_BYTES);
        in.reset();

        boolean contentMatches = "pdf".equals(ext) ? startsWith(head, PDF_MAGIC) : looksLikeText(head);
        if (!contentMatches) {
            throw new UnsupportedDocumentTypeException("conteúdo do arquivo não corresponde à extensão ." + ext);
        }
        return new ValidatedUpload(fileName, mimeType, in);
    }

    static String sanitizeFileName(String original) {
        if (original == null) original = "";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);           // remove qualquer diretório
        name = name.replaceAll("[\\p{Cntrl}<>\"'`]", "").strip();  // controle e caracteres de markup
        if (name.length() > 255) name = name.substring(name.length() - 255);
        if (name.isBlank() || name.startsWith(".")) {
            throw new UnsupportedDocumentTypeException("nome de arquivo inválido");
        }
        return name;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean looksLikeText(byte[] data) {
        if (data.length == 0) return false;
        for (byte b : data) {
            if (b == 0) return false;   // binários (exe, zip, imagens) quase sempre têm NUL
        }
        return true;
    }
}
