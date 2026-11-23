package ru.posts.grabber.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrabberSourceScanTest {

    @Test
    void oneBadSourceDoesNotFailTheRun() {
        List<String> failed = new ArrayList<>();
        List<String> items = GrabberSourceScan.collect(
                List.of("team", "-123", "club"),
                sourceId -> {
                    if ("team".equals(sourceId)) {
                        throw new IllegalStateException("VK wall.get failed: access_token=secret-token");
                    }
                    return List.of(sourceId + ":post");
                },
                (sourceId, ex) ->
                        failed.add(sourceId + ":" + GrabberSourceScan.redact(ex.getMessage(), "secret-token")));
        assertThat(items).containsExactly("-123:post", "club:post");
        assertThat(failed).containsExactly("team:VK wall.get failed: access_token=[redacted]");
    }

    @Test
    void communityLookupNegatesPositiveIdAndUsesDomainForScreenName() {
        assertThat(VkWallFetcher.lookup("12345")).isEqualTo(new VkWallFetcher.WallLookup("owner_id", "-12345"));
        assertThat(VkWallFetcher.lookup("-8")).isEqualTo(new VkWallFetcher.WallLookup("owner_id", "-8"));
        VkWallFetcher.WallLookup team = VkWallFetcher.lookup("team");
        assertThat(team.param()).isEqualTo("domain");
        assertThat(team.value()).isEqualTo("team");
        assertThat(team.param()).isNotEqualTo("owner_id");
    }
}
