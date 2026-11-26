package ru.posts.social.api;

import java.time.Duration;
import java.util.Optional;

/** Checked-style runtime exception carrying {@link ErrorClass} and optional retry-after. */
public class SocialApiException extends RuntimeException {

    private final ErrorClass errorClass;
    private final Duration retryAfter;
    private final Integer httpStatus;
    /** Provider error code (VK {@code error_code}), when the call returned one. */
    private final String providerCode;

    public SocialApiException(ErrorClass errorClass, String message) {
        this(errorClass, message, null, null, null);
    }

    public SocialApiException(ErrorClass errorClass, String message, Integer httpStatus) {
        this(errorClass, message, httpStatus, null, null);
    }

    public SocialApiException(
            ErrorClass errorClass, String message, Integer httpStatus, Duration retryAfter, Throwable cause) {
        this(errorClass, message, httpStatus, retryAfter, cause, null);
    }

    public SocialApiException(
            ErrorClass errorClass,
            String message,
            Integer httpStatus,
            Duration retryAfter,
            Throwable cause,
            String providerCode) {
        super(message, cause);
        this.errorClass = errorClass;
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
        this.providerCode = providerCode;
    }

    public ErrorClass errorClass() {
        return errorClass;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public Optional<Integer> httpStatus() {
        return Optional.ofNullable(httpStatus);
    }

    public Optional<String> providerCode() {
        return Optional.ofNullable(providerCode);
    }
}
