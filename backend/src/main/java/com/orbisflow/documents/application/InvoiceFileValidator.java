package com.orbisflow.documents.application;

import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class InvoiceFileValidator {
    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_EOF = "%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_HEADER =
            {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] PNG_IEND =
            {0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44,
                    (byte) 0xae, 0x42, 0x60, (byte) 0x82};
    private static final Map<String, SignatureCheck> SIGNATURES = Map.of(
            "application/pdf", InvoiceFileValidator::validPdf,
            "image/jpeg", InvoiceFileValidator::validJpeg,
            "image/png", InvoiceFileValidator::validPng);

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile("The uploaded file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.FILE_TOO_LARGE,
                    "The uploaded file exceeds the 10 MB limit.");
        }
        String contentType = file.getContentType();
        SignatureCheck signature = SIGNATURES.get(contentType);
        if (signature == null) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF, JPG, and PNG files are accepted.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw invalidFile("The uploaded file could not be read.");
        }
        if (bytes.length == 0 || bytes.length > MAX_FILE_SIZE
                || !signature.matches(bytes)) {
            if (bytes.length > MAX_FILE_SIZE) {
                throw new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.FILE_TOO_LARGE,
                        "The uploaded file exceeds the 10 MB limit.");
            }
            throw invalidFile(
                    "The file content is corrupt or does not match its declared media type.");
        }
        return new ValidatedUpload(
                bytes, contentType, safeFilename(file.getOriginalFilename()));
    }

    private static boolean validPdf(byte[] bytes) {
        if (!startsWith(bytes, PDF_HEADER)) {
            return false;
        }
        int searchStart = Math.max(0, bytes.length - 1024);
        return indexOf(bytes, PDF_EOF, searchStart) >= 0;
    }

    private static boolean validJpeg(byte[] bytes) {
        return bytes.length >= 5
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff
                && (bytes[bytes.length - 2] & 0xff) == 0xff
                && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }

    private static boolean validPng(byte[] bytes) {
        return startsWith(bytes, PNG_HEADER)
                && bytes.length >= PNG_HEADER.length + PNG_IEND.length
                && Arrays.equals(
                        Arrays.copyOfRange(
                                bytes, bytes.length - PNG_IEND.length, bytes.length),
                        PNG_IEND);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }

    private static int indexOf(byte[] bytes, byte[] needle, int start) {
        outer:
        for (int i = start; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String safeFilename(String original) {
        String candidate = original == null || original.isBlank()
                ? "invoice" : original.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (candidate.isEmpty()) {
            candidate = "invoice";
        }
        return candidate.length() > 255 ? candidate.substring(0, 255) : candidate;
    }

    private static ApiException invalidFile(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_FILE, message);
    }

    @FunctionalInterface
    private interface SignatureCheck {
        boolean matches(byte[] bytes);
    }

    public record ValidatedUpload(byte[] bytes, String mimeType, String filename) {
    }
}
