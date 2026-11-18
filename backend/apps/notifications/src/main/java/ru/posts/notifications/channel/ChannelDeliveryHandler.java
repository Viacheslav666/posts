package ru.posts.notifications.channel;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.persistence.entity.NotificationDeliveryEntity;
import ru.posts.persistence.repository.NotificationDeliveryRepository;

@Component
public class ChannelDeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(ChannelDeliveryHandler.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryStatusUpdater statusUpdater;
    private final EmailDeliveryService emailDeliveryService;
    private final TelegramDeliveryService telegramDeliveryService;
    private final WebPushDeliveryService webPushDeliveryService;
    private final WsDeliveryService wsDeliveryService;

    public ChannelDeliveryHandler(
            NotificationDeliveryRepository deliveryRepository,
            DeliveryStatusUpdater statusUpdater,
            EmailDeliveryService emailDeliveryService,
            TelegramDeliveryService telegramDeliveryService,
            WebPushDeliveryService webPushDeliveryService,
            WsDeliveryService wsDeliveryService) {
        this.deliveryRepository = deliveryRepository;
        this.statusUpdater = statusUpdater;
        this.emailDeliveryService = emailDeliveryService;
        this.telegramDeliveryService = telegramDeliveryService;
        this.webPushDeliveryService = webPushDeliveryService;
        this.wsDeliveryService = wsDeliveryService;
    }

    /** Handles a channel delivery command and returns {@code true} when the broker message may be acked. */
    public boolean handle(NotifyDeliveryCommand command) {
        if (command == null || command.deliveryId() == null) {
            log.error("Malformed delivery command");
            return false;
        }
        Optional<NotificationDeliveryEntity> opt = deliveryRepository.findById(command.deliveryId());
        if (opt.isEmpty()) {
            log.warn("Delivery {} not found — ack", command.deliveryId());
            return true;
        }
        NotificationDeliveryEntity delivery = opt.get();
        if ("sent".equals(delivery.getStatus())
                || "skipped".equals(delivery.getStatus())
                || "dead".equals(delivery.getStatus())) {
            return true;
        }

        DeliveryOutcome outcome =
                switch (command.channel()) {
                    case "email" -> emailDeliveryService.deliver(command);
                    case "telegram" -> telegramDeliveryService.deliver(command);
                    case "web_push" -> webPushDeliveryService.deliver(command);
                    case "ws" -> wsDeliveryService.deliver(command);
                    default -> DeliveryOutcome.skipped("unknown_channel");
                };
        statusUpdater.apply(delivery, outcome);
        // failed deliveries are retried by scheduler — always ack to avoid DLQ storms
        return true;
    }
}
