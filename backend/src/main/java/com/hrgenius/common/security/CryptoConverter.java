package com.hrgenius.common.security;

import jakarta.persistence.AttributeConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA {@link AttributeConverter} that transparently AES-GCM encrypts sensitive columns
 * (PAN, Aadhaar/SSN, bank account) at rest. Applied per-field via @Convert on entities.
 *
 * <p>The key comes from config (hrgenius.security.encryption-key, 32 bytes / 256-bit).
 * Ciphertext is stored as Base64(IV || cipherText).
 */
@Component
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGO = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    /**
     * Spring Boot registers Spring's bean container with Hibernate, so JPA uses this
     * Spring-managed instance (with the injected key) rather than instantiating it reflectively.
     */
    public CryptoConverter(@Value("${hrgenius.security.encryption-key}") String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        // Normalise to 32 bytes (AES-256).
        byte[] normalized = Arrays.copyOf(raw, 32);
        this.keySpec = new SecretKeySpec(normalized, ALGO);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt sensitive field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt sensitive field", e);
        }
    }
}
