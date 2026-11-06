package com.calisthenics.backend.security.registration;

import lombok.Getter;

@Getter
public class EmailAddressException extends IllegalArgumentException {

    private final AddressField field;
    private final String sanitizedAddress;

    public EmailAddressException(AddressField field, String sanitizedAddress, String message) {
        super(message);
        this.field = field;
        this.sanitizedAddress = sanitizedAddress;
    }

    public EmailAddressException(AddressField field, String sanitizedAddress, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
        this.sanitizedAddress = sanitizedAddress;
    }
}

