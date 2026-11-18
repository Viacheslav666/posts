package ru.posts.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Amount(@JsonProperty("value") String value, @JsonProperty("currency") String currency) {}
