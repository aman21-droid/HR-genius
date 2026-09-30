package com.hrgenius.common.storage;

import com.hrgenius.common.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private static final String ALLOWED = "application/pdf,image/png,image/jpeg";

    @Test
    void storesAndLoadsAValidPdf(@TempDir Path dir) throws Exception {
        FileStorageService s = new FileStorageService(dir.toString(), ALLOWED);
        byte[] pdf = "%PDF-1.4 hello".getBytes(StandardCharsets.US_ASCII);
        String key = s.store(pdf, "application/pdf");
        assertThat(s.load(key).getContentAsByteArray()).isEqualTo(pdf);
    }

    @Test
    void rejectsHtmlDisguisedAsPdf(@TempDir Path dir) {
        FileStorageService s = new FileStorageService(dir.toString(), ALLOWED);
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> s.store(html, "application/pdf"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("does not match");
    }

    @Test
    void rejectsTypesOffTheAllowList(@TempDir Path dir) {
        FileStorageService s = new FileStorageService(dir.toString(), ALLOWED);
        assertThatThrownBy(() -> s.store("x".getBytes(), "text/html"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsPathTraversalKeys(@TempDir Path dir) {
        FileStorageService s = new FileStorageService(dir.toString(), ALLOWED);
        assertThatThrownBy(() -> s.load("../../secret")).isInstanceOf(BadRequestException.class);
    }
}
