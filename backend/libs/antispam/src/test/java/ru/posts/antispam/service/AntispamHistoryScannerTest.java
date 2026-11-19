package ru.posts.antispam.service;

import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.social.inbound.SocialInboundEvent;

@ExtendWith(MockitoExtension.class)
class AntispamHistoryScannerTest {

    @Mock
    AntispamEventProcessor processor;

    @Test
    void scanFeedsWallCommentsIntoTheEngine() throws Exception {
        String body =
                "{\"response\":{\"count\":1,\"items\":[{\"id\":7,\"from_id\":3,\"post_id\":9,\"text\":\"buy now\"}]}}";
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/method/wall.getComments", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ChannelEntity channel = new ChannelEntity();
            channel.setId(UUID.randomUUID());
            channel.setWorkspaceId(UUID.randomUUID());
            channel.setExternalId("-50");
            AntispamHistoryScanner scanner = new AntispamHistoryScanner(
                    processor, "http://127.0.0.1:" + server.getAddress().getPort(), "5.199");
            int scanned = scanner.scan(new AntispamRuleEntity(), channel, "token", 100);
            assert scanned == 1;
            ArgumentCaptor<SocialInboundEvent> event = ArgumentCaptor.forClass(SocialInboundEvent.class);
            verify(processor).process(event.capture());
            org.assertj.core.api.Assertions.assertThat(event.getValue().text()).isEqualTo("buy now");
            org.assertj.core.api.Assertions.assertThat(
                            event.getValue().remoteIds().get("comment_id"))
                    .isEqualTo("7");
        } finally {
            server.stop(0);
        }
    }
}
