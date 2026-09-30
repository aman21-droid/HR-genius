package com.hrgenius.common.storage;

import com.hrgenius.common.error.BadRequestException;
import com.hrgenius.common.error.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stores uploaded files on disk under random UUID keys.
 *
 * <p>Security properties:
 * <ul>
 *   <li>The client-supplied filename never touches the filesystem, and keys are validated
 *       against a strict UUID pattern on read, so path traversal is impossible.</li>
 *   <li>The declared content type must be on an allow-list AND match the file's magic bytes,
 *       so e.g. an HTML page renamed to .pdf is rejected.</li>
 * </ul>
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path root;
    private final Set<String> allowedContentTypes;

    public FileStorageService(@Value("${hrgenius.storage.root}") String root,
                              @Value("${hrgenius.storage.allowed-content-types}") String allowed) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.allowedContentTypes = Arrays.stream(allowed.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage root " + this.root, e);
        }
        log.info("File storage root: {}", this.root);
    }

    /** Validates and stores the bytes; returns the opaque storage key. */
    public String store(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("The file is empty");
        }
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new BadRequestException("Unsupported file type. Allowed: PDF, PNG, JPEG, DOCX");
        }
        if (!magicBytesMatch(content, contentType)) {
            throw new BadRequestException("File content does not match its declared type");
        }
        String key = UUID.randomUUID().toString();
        Path target = pathFor(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        return key;
    }

    public Resource load(String key) {
        Path path = pathFor(key);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("The file is no longer available in storage");
        }
        return new PathResource(path);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(pathFor(key));
        } catch (IOException e) {
            // Metadata is soft-deleted regardless; an orphaned file is harmless.
            log.warn("Could not delete stored file {}", key, e);
        }
    }

    /** Two-level fan-out (ab/abcd...) keeps directories small. */
    private Path pathFor(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new BadRequestException("Invalid storage key");
        }
        Path path = root.resolve(key.substring(0, 2)).resolve(key).normalize();
        if (!path.startsWith(root)) {        // defence in depth; unreachable given KEY_PATTERN
            throw new BadRequestException("Invalid storage key");
        }
        return path;
    }

    static boolean magicBytesMatch(byte[] b, String contentType) {
        return switch (contentType) {
            case "application/pdf" -> startsWith(b, 0x25, 0x50, 0x44, 0x46);               // %PDF
            case "image/png" -> startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(b, 0xFF, 0xD8, 0xFF);
            // DOCX is a ZIP container
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    startsWith(b, 0x50, 0x4B, 0x03, 0x04);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] b, int... sig) {
        if (b.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((b[i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }
}
