package ru.posts.api.web.support;

import ru.posts.api.error.NotImplementedException;

/** Shared helper for BE-3 stub controllers. */
public final class ApiStub {

    private ApiStub() {}

    /** Prefer {@code throw ApiStub.notImplemented(...)} so the compiler knows control does not continue. */
    public static NotImplementedException notImplemented(String operationId) {
        return new NotImplementedException(operationId);
    }
}
