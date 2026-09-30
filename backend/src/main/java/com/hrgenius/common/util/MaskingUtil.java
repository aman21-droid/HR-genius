package com.hrgenius.common.util;

/**
 * Masks sensitive identifiers for display and audit logs, keeping only the last few
 * characters (e.g. "XXXXXXXX1234"). Plaintext sensitive values must never be written
 * to logs or the audit trail.
 */
public final class MaskingUtil {

    private static final int VISIBLE = 4;

    private MaskingUtil() {
    }

    public static String mask(String value) {
        return mask(value, VISIBLE);
    }

    public static String mask(String value, int visible) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int len = value.length();
        if (len <= visible) {
            return "X".repeat(len);
        }
        return "X".repeat(len - visible) + value.substring(len - visible);
    }
}
