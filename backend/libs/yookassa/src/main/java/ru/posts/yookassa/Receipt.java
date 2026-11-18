package ru.posts.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Receipt(@JsonProperty("customer") Customer customer, @JsonProperty("items") List<ReceiptItem> items) {

    public record Customer(@JsonProperty("email") String email) {}

    public record ReceiptItem(
            @JsonProperty("description") String description,
            @JsonProperty("quantity") String quantity,
            @JsonProperty("amount") Amount amount,
            @JsonProperty("vat_code") int vatCode) {}
}
