package ru.posts.yookassa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentMethod(
        @JsonProperty("type") String type, @JsonProperty("id") String id, @JsonProperty("saved") Boolean saved) {}
