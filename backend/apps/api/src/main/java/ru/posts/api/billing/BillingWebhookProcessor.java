package ru.posts.api.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.posts.persistence.entity.BillingWebhookInboxEntity;
import ru.posts.persistence.repository.BillingWebhookInboxRepository;
import ru.posts.yookassa.PaymentResponse;
import ru.posts.yookassa.YooKassaClient;
import ru.posts.yookassa.YooKassaException;

@Component
@Profile("!openapi")
public class BillingWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookProcessor.class);

    private final BillingWebhookInboxRepository inboxRepository;
    private final YooKassaClient yooKassaClient;
    private final BillingPaymentProcessor paymentProcessor;

    public BillingWebhookProcessor(
            BillingWebhookInboxRepository inboxRepository,
            YooKassaClient yooKassaClient,
            BillingPaymentProcessor paymentProcessor) {
        this.inboxRepository = inboxRepository;
        this.yooKassaClient = yooKassaClient;
        this.paymentProcessor = paymentProcessor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWebhookCommitted(YooKassaWebhookReceivedEvent event) {
        processInbox(event.inboxId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processInbox(UUID inboxId) {
        BillingWebhookInboxEntity inbox = inboxRepository.findById(inboxId).orElse(null);
        if (inbox == null || !"pending".equals(inbox.getStatus())) {
            return;
        }
        try {
            String outcome = applyEvent(inbox);
            inbox.setStatus(outcome);
            inbox.setProcessedAt(Instant.now());
            inbox.setErrorMessage(null);
            inboxRepository.save(inbox);
        } catch (Exception ex) {
            log.error("Failed to process YooKassa webhook inbox {}", inboxId, ex);
            String message = ex.getMessage() == null ? "error" : ex.getMessage();
            inbox.setStatus("failed");
            inbox.setErrorMessage(message.substring(0, Math.min(message.length(), 1000)));
            inbox.setProcessedAt(Instant.now());
            inboxRepository.save(inbox);
        }
    }

    private String applyEvent(BillingWebhookInboxEntity inbox) {
        String eventType = inbox.getEventType();
        if (eventType == null) {
            return "ignored";
        }
        return switch (eventType) {
            case "payment.succeeded" -> {
                PaymentResponse verified = fetchPayment(inbox.getProviderPaymentId());
                if (!"succeeded".equals(verified.status()) && !Boolean.TRUE.equals(verified.paid())) {
                    throw new YooKassaException("Provider payment not succeeded: " + verified.status());
                }
                paymentProcessor.onPaymentSucceeded(verified);
                yield "processed";
            }
            case "payment.canceled", "payment.cancelled" -> {
                PaymentResponse verified = fetchPayment(inbox.getProviderPaymentId());
                paymentProcessor.onPaymentCanceled(verified);
                yield "processed";
            }
            case "refund.succeeded" -> {
                Map<String, Object> payload = inbox.getPayload();
                Object object = payload == null ? null : payload.get("object");
                String paymentId = inbox.getProviderPaymentId();
                BigDecimal amount = null;
                if (object instanceof Map<?, ?> map) {
                    Object paymentIdObj = map.get("payment_id");
                    if (paymentIdObj != null) {
                        paymentId = paymentIdObj.toString();
                    }
                    Object amountObj = map.get("amount");
                    if (amountObj instanceof Map<?, ?> amountMap && amountMap.get("value") != null) {
                        amount = new BigDecimal(amountMap.get("value").toString());
                    }
                }
                paymentProcessor.onRefundSucceeded(paymentId, amount);
                yield "processed";
            }
            default -> {
                log.info("Ignoring YooKassa event type {}", eventType);
                yield "ignored";
            }
        };
    }

    private PaymentResponse fetchPayment(String providerPaymentId) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new YooKassaException("Missing provider payment id in webhook");
        }
        return yooKassaClient.getPayment(providerPaymentId);
    }
}
