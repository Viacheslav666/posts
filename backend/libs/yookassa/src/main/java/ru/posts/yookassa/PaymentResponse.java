package ru.posts.yookassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("paid") Boolean paid,
        @JsonProperty("amount") Amount amount,
        @JsonProperty("confirmation") Confirmation confirmation,
        @JsonProperty("payment_method") PaymentMethod paymentMethod,
        @JsonProperty("metadata") Map<String, String> metadata,
        @JsonProperty("cancellation_details") CancellationDetails cancellationDetails) {}
