package com.badwolfmc.guardian.protection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/** Deterministic normalization for configured roots, player commands, and suggestion buffers. */
public final class CommandRootNormalizer {
    public static final int MAX_ROOT_LENGTH = 96;
    private static final int MAX_PERMISSION_KEY_LENGTH = 64;

    private CommandRootNormalizer() {
    }

    public static CommandRoot normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("command root must not be null");
        }

        String candidate = input.strip();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("command root must not be blank");
        }

        // Bukkit player command messages contain one transport slash. Strip exactly one so
        // /plugins and plugins normalize identically without collapsing deliberate // aliases.
        if (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }

        int firstWhitespace = firstWhitespace(candidate);
        if (firstWhitespace >= 0) {
            candidate = candidate.substring(0, firstWhitespace);
        }
        candidate = candidate.toLowerCase(Locale.ROOT);

        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("command root must not be blank");
        }
        if (candidate.length() > MAX_ROOT_LENGTH) {
            throw new IllegalArgumentException(
                "command root exceeds " + MAX_ROOT_LENGTH + " characters after normalization");
        }
        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                throw new IllegalArgumentException("command root contains whitespace/control characters");
            }
        }

        int colon = candidate.indexOf(':');
        boolean namespaced = colon > 0
            && colon == candidate.lastIndexOf(':')
            && colon < candidate.length() - 1;
        String namespace = namespaced ? candidate.substring(0, colon) : "";
        String label = namespaced ? candidate.substring(colon + 1) : candidate;

        return new CommandRoot(candidate, namespaced, namespace, label, permissionKey(candidate));
    }

    public static Optional<CommandRoot> tryNormalize(String input) {
        try {
            return Optional.of(normalize(input));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String permissionKey(String root) {
        byte[] bytes = root.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte raw : bytes) {
            int value = raw & 0xff;
            char ch = (char) value;
            if ((ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '.' || ch == '_' || ch == '-') {
                encoded.append(ch);
            } else {
                encoded.append('_');
                encoded.append(Character.forDigit((value >>> 4) & 0xf, 16));
                encoded.append(Character.forDigit(value & 0xf, 16));
            }
        }

        if (encoded.length() <= MAX_PERMISSION_KEY_LENGTH) {
            return encoded.toString();
        }
        return "sha256-" + sha256(root).substring(0, 32);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
