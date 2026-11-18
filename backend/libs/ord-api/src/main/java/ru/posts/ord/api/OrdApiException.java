package ru.posts.ord.api;

import java.util.Optional;

/** Runtime exception carrying {@link OrdErrorClass} and optional HTTP status. */
public class OrdApiException extends RuntimeException {

    private final OrdErrorClass errorClass;
    private final Integer httpStatus;

    public OrdApiException(OrdErrorClass errorClass, String message) {
        this(errorClass, message, null, null);
    }

    public OrdApiException(OrdErrorClass errorClass, String message, Integer httpStatus) {
        this(errorClass, message, httpStatus, null);
    }

    public OrdApiException(OrdErrorClass errorClass, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.errorClass = errorClass;
        this.httpStatus = httpStatus;
    }

    public OrdErrorClass errorClass() {
        return errorClass;
    }

    public Optional<Integer> httpStatus() {
        return Optional.ofNullable(httpStatus);
    }
}
