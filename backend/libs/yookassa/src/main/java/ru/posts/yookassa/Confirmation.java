package ru.posts.yookassa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Confirmation(
        @JsonProperty("type") String type,
        @JsonProperty("return_url") String returnUrl,
        @JsonProperty("confirmation_url") String confirmationUrl) {

    public static Confirmation redirect(String returnUrl) {
        return new Confirmation("redirect", returnUrl, null);
    }
}
