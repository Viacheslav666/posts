package ru.posts.yookassa;

public class YooKassaException extends RuntimeException {

    private final Integer httpStatus;

    public YooKassaException(String message) {
        this(message, null, null);
    }

    public YooKassaException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public YooKassaException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
