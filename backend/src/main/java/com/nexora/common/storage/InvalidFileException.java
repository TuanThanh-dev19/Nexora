package com.nexora.common.storage;

public class InvalidFileException extends RuntimeException {
    public enum Reason {
        INVALID,
        EMPTY,
        TOO_LARGE,
        UNSUPPORTED_MEDIA,
        COUNT,
        SIGNATURE
    }

    private final Reason reason;

    public InvalidFileException(String message) {
        this(Reason.INVALID, message);
    }

    public InvalidFileException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
