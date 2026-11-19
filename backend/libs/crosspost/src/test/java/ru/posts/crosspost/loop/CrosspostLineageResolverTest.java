package ru.posts.crosspost.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;

@ExtendWith(MockitoExtension.class)
class CrosspostLineageResolverTest {

    @Mock
    RemotePostRegistryJdbc registry;

    @Test
    void resolveUsesRegistryRootOriginAndHop() {
        UUID channel = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        String rootJson =
                """
                {"network":"vk","channel_id":"%s","remote_post_id":"orig-1"}
                """
                        .formatted(root);
        when(registry.findOwn(channel, "post-copy"))
                .thenReturn(
                        Optional.of(new RemotePostRegistryJdbc.RegistryEntry("crosspost", (short) 2, "fp", rootJson)));

        CrosspostItemEntity item = new CrosspostItemEntity();
        item.setSourceChannelId(channel);
        item.setSourceRemoteId("post-copy");
        item.setSourceNetwork("vk");
        item.setPayload(Map.of("hop", 0));

        CrosspostLineageResolver.Resolved resolved = CrosspostLineageResolver.resolve(item, registry);
        assertThat(resolved.hop()).isEqualTo((short) 2);
        assertThat(resolved.sourceRef().get("root_origin")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> rootOrigin =
                (Map<String, Object>) resolved.sourceRef().get("root_origin");
        assertThat(rootOrigin.get("channel_id")).isEqualTo(root.toString());
        assertThat(resolved.sourceRef().get("visited_channel_ids")).isInstanceOf(List.class);
    }
}
