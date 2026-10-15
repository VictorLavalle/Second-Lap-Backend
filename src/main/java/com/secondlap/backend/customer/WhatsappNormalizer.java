package com.secondlap.backend.customer;

public final class WhatsappNormalizer {

    private WhatsappNormalizer() {
    }

    /**
     * Reduce any phone input to its 10 significant digits, dropping separators,
     * spaces and an optional country prefix. This is the natural dedup key, so it
     * must produce the same value regardless of how the user typed the number.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("whatsapp is required");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.length() != 10) {
            throw new IllegalArgumentException("whatsapp must contain 10 digits");
        }
        return digits;
    }
}
