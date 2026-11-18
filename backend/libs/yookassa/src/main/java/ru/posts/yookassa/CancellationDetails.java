package ru.posts.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CancellationDetails(@JsonProperty("party") String party, @JsonProperty("reason") String reason) {}
