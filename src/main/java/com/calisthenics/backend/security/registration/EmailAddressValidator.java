package com.calisthenics.backend.security.registration;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EmailAddressValidator {

    private EmailAddressValidator() {
        // Utility class
    }

    public static InternetAddress validateAndParse(AddressField field, String rawAddress) {
        return validateAndParse(field, rawAddress, false);
    }

    public static InternetAddress validateAndParse(AddressField field, String rawAddress, boolean allowPersonalName) {
        if (rawAddress == null) {
            logFailure(field, null, 0, false, false, "Address must not be null");
            throw new EmailAddressException(field, "null", "Email address for " + field + " must not be null");
        }

        int originalLength = rawAddress.length();
        boolean hasWhitespace = containsWhitespace(rawAddress);
        boolean hasControlChars = containsControlChars(rawAddress);
        boolean hasNewline = containsNewline(rawAddress);

        if (rawAddress.isBlank()) {
            logFailure(field, rawAddress, originalLength, hasWhitespace, hasControlChars, "Address is blank");
            throw new EmailAddressException(field, sanitize(rawAddress), "Email address for " + field + " is blank");
        }

        if (hasNewline || hasControlChars) {
            logFailure(field, rawAddress, originalLength, hasWhitespace, true, "Address contains illegal newline or control characters");
            throw new EmailAddressException(
                    field,
                    sanitize(rawAddress),
                    "Email address for " + field + " contains illegal newline or control characters"
            );
        }

        String normalized = rawAddress.strip();

        if (normalized.isEmpty()) {
            logFailure(field, rawAddress, originalLength, true, false, "Address is empty after stripping whitespace");
            throw new EmailAddressException(field, sanitize(rawAddress), "Email address for " + field + " is empty after normalization");
        }

        if (!allowPersonalName && containsWhitespace(normalized)) {
            logFailure(field, rawAddress, originalLength, true, false, "Address must not contain internal whitespace");
            throw new EmailAddressException(field, sanitize(rawAddress), "Email address for " + field + " contains illegal whitespace");
        }

        InternetAddress[] parsed;
        try {
            parsed = InternetAddress.parse(normalized, false);
        } catch (AddressException ex) {
            logFailure(field, rawAddress, originalLength, hasWhitespace, false, "Malformed address syntax: " + ex.getMessage());
            throw new EmailAddressException(field, sanitize(rawAddress), "Malformed email address syntax for " + field + ": " + ex.getMessage(), ex);
        }

        if (parsed == null || parsed.length != 1) {
            int count = parsed == null ? 0 : parsed.length;
            logFailure(field, rawAddress, originalLength, hasWhitespace, false, "Expected exactly 1 address, but parsed " + count);
            throw new EmailAddressException(field, sanitize(rawAddress), "Expected exactly 1 email address for " + field + ", but found " + count);
        }

        InternetAddress internetAddress = parsed[0];
        try {
            internetAddress.validate();
        } catch (AddressException ex) {
            logFailure(field, rawAddress, originalLength, hasWhitespace, false, "Address failed RFC validation: " + ex.getMessage());
            throw new EmailAddressException(field, sanitize(rawAddress), "Invalid email address format for " + field + ": " + ex.getMessage(), ex);
        }

        String actualEmail = internetAddress.getAddress();
        if (actualEmail == null || !actualEmail.contains("@")) {
            logFailure(field, rawAddress, originalLength, hasWhitespace, false, "Address missing '@' symbol or domain");
            throw new EmailAddressException(field, sanitize(rawAddress), "Email address for " + field + " is missing '@' or domain");
        }

        String[] parts = actualEmail.split("@", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank() || !parts[1].contains(".")) {
            logFailure(field, rawAddress, originalLength, hasWhitespace, false, "Address has invalid local-part or domain");
            throw new EmailAddressException(field, sanitize(rawAddress), "Email address for " + field + " has invalid local-part or domain");
        }

        return internetAddress;
    }

    public static boolean containsNewline(String value) {
        if (value == null) {
            return false;
        }
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    public static boolean containsControlChars(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsWhitespace(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static void logFailure(
            AddressField field,
            String rawAddress,
            int length,
            boolean hasWhitespace,
            boolean hasControlChars,
            String reason
    ) {
        String sanitized = sanitize(rawAddress);
        log.error(
                "Email address validation failed: field={}, sanitized='{}', length={}, hasWhitespace={}, hasControlChars={}, reason='{}'",
                field, sanitized, length, hasWhitespace, hasControlChars, reason
        );
    }

    public static String sanitize(String address) {
        if (address == null) {
            return "null";
        }
        if (address.isBlank()) {
            return "[blank]";
        }

        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (c == '\n') {
                escaped.append("\\n");
            } else if (c == '\r') {
                escaped.append("\\r");
            } else if (c == '\t') {
                escaped.append("\\t");
            } else if (Character.isISOControl(c)) {
                escaped.append(String.format("\\u%04x", (int) c));
            } else {
                escaped.append(c);
            }
        }

        String cleaned = escaped.toString();
        int atIdx = cleaned.indexOf('@');
        if (atIdx > 0) {
            String local = cleaned.substring(0, atIdx);
            String domain = cleaned.substring(atIdx + 1);
            String maskedLocal;
            if (local.length() <= 2) {
                maskedLocal = local.charAt(0) + "*";
            } else {
                maskedLocal = local.substring(0, 2) + "***" + local.charAt(local.length() - 1);
            }
            return maskedLocal + "@" + domain;
        } else {
            if (cleaned.length() <= 3) {
                return "***";
            }
            return cleaned.substring(0, Math.min(2, cleaned.length())) + "***" + cleaned.charAt(cleaned.length() - 1);
        }
    }
}

