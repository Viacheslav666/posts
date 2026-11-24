package ru.posts.grabber.target;

/** Postponed VK publish_date would fall more than 7 days ahead of now. */
public class PostponedHorizonException extends IllegalArgumentException {

    public PostponedHorizonException(String message) {
        super(message);
    }
}
