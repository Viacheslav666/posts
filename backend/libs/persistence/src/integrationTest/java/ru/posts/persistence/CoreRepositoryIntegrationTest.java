package ru.posts.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.PlanEntity;
import ru.posts.persistence.entity.PostEntity;
import ru.posts.persistence.entity.PostVariantEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;

@SpringBootTest(classes = PersistenceIntegrationTestApp.class)
@Testcontainers
@Transactional
class CoreRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostVariantRepository postVariantRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Test
    void persistsTenantGraphAndPublication() throws Exception {
        PlanEntity pro = planRepository.findByCode("pro").orElseThrow();

        UserEntity user = new UserEntity();
        user.setEmail("owner-" + UuidV7.generateFast() + "@example.com");
        user.setName("Owner");
        user = userRepository.save(user);

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName("Acme");
        workspace.setOwnerUserId(user.getId());
        workspace.setPlanId(pro.getId());
        workspace.setBillingStatus("trial");
        workspace = workspaceRepository.save(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(user.getId());
        member.setRole("owner");
        workspaceMemberRepository.save(member);

        ChannelEntity channel = new ChannelEntity();
        channel.setWorkspaceId(workspace.getId());
        channel.setNetwork("tg");
        channel.setExternalId("-1001");
        channel.setType("channel");
        channel.setTitle("News");
        channel = channelRepository.save(channel);

        PostEntity post = new PostEntity();
        post.setWorkspaceId(workspace.getId());
        post.setAuthorId(user.getId());
        post.setTitle("Hello");
        post.setBaseContent(Map.of("text", "Hello world"));
        post.setStatus("scheduled");
        post.setScheduledAt(Instant.now().plus(1, ChronoUnit.HOURS));
        post = postRepository.save(post);

        PostVariantEntity variant = new PostVariantEntity();
        variant.setPostId(post.getId());
        variant.setChannelId(channel.getId());
        variant.setNetwork("tg");
        variant.setContent(Map.of("text", "Hello TG"));
        variant = postVariantRepository.save(variant);

        Instant runAt = Instant.parse("2026-11-20T12:00:00Z");
        PublicationEntity publication = new PublicationEntity();
        publication.setWorkspaceId(workspace.getId());
        publication.setPostId(post.getId());
        publication.setVariantId(variant.getId());
        publication.setChannelId(channel.getId());
        publication.setNetwork("tg");
        publication.setRunAt(runAt);
        publication.setIdempotencyKey(sha256(variant.getId() + "|" + runAt));
        publication.setState("pending");
        publication = publicationRepository.saveAndFlush(publication);

        assertThat(publication.getId()).isNotNull();
        assertThat(UuidV7.isUuidV7(publication.getId())).isTrue();
        assertThat(publicationRepository.findByPostId(post.getId())).hasSize(1);
        assertThat(workspaceMemberRepository.findByWorkspaceId(workspace.getId()))
                .hasSize(1);
        assertThat(channelRepository.findByWorkspaceIdAndNetworkAndExternalId(workspace.getId(), "tg", "-1001"))
                .isPresent();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
