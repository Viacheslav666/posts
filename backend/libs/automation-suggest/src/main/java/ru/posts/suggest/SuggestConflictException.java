package ru.posts.suggest;

/** Reject/approve is illegal for the item's current status or a running remote action. */
public class SuggestConflictException extends RuntimeException {

    public SuggestConflictException(String message) {
        super(message);
    }
}
