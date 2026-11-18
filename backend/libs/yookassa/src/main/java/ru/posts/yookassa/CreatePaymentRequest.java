package ru.posts.yookassa;

import java.util.Map;

/**
 * Input for {@link YooKassaClient#createPayment}. The client maps this to the YooKassa create-payment
 * body ({@code capture}, redirect confirmation, {@code save_payment_method}, optional receipt).
 */
public record CreatePaymentRequest(
        Amount amount, String description, Map<String, String> metadata, Receipt receipt, String returnUrl) {

    public CreatePaymentRequest(Amount amount, String description, Map<String, String> metadata, Receipt receipt) {
        this(amount, description, metadata, receipt, null);
    }
}
