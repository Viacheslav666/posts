package ru.posts.api.error;

import org.springframework.http.HttpStatus;

/** Thrown by stub controllers until the corresponding BE sprint implements the operation. */
public class NotImplementedException extends ApiException {

    public NotImplementedException(String operationId) {
        super(
                HttpStatus.NOT_IMPLEMENTED,
                ProblemCodes.NOT_IMPLEMENTED,
                "Operation not implemented yet: " + operationId);
    }
}
