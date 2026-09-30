package com.hrgenius.common.web;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Builds file-download responses with safe headers: always an attachment (never rendered
 * inline), nosniff so browsers honour the declared type, and no caching of personal data.
 */
public final class Downloads {

    public static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Downloads() {
    }

    public static ResponseEntity<Resource> attachment(Resource body, String fileName, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).contentType(type).body(body);
    }

    public static ResponseEntity<Resource> attachment(byte[] body, String fileName, MediaType type) {
        return attachment(new ByteArrayResource(body), fileName, type);
    }
}
