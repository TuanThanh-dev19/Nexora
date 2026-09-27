package com.nexora.common.exception;

public class StaleWriteException extends RuntimeException {

    public StaleWriteException() {
        super("The resource was changed by another request. Reload it and retry your update.");
    }
}
