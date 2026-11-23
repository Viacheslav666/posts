package ru.posts.grabber.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.media.GrabberMediaResolver;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PostRepository;
import ru.posts.persistence.repository.PostVariantRepository;
import ru.posts.persistence.repository.PublicationRepository;

@ExtendWith(MockitoExtension.class)
class GrabberPostFactoryTransactionTest {

    @Mock
    PostRepository postRepository;

    @Mock
    PostVariantRepository postVariantRepository;

    @Mock
    PublicationRepository publicationRepository;

    @Mock
    ChannelRepository channelRepository;

    @Mock
    GrabberItemRepository itemRepository;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    GrabberMediaResolver mediaResolver;

    private GrabberPostFactory factory;
    private ChannelEntity channel;
    private AutomationRuleEntity rule;
    private final GrabberItemEntity row = new GrabberItemEntity();

    @BeforeEach
    void setUp() {
        factory = new GrabberPostFactory(
                postRepository,
                postVariantRepository,
                publicationRepository,
                channelRepository,
                itemRepository,
                outboxRepository,
                mediaResolver,
                new FlaggingTransactionManager());
        channel = new ChannelEntity();
        channel.setId(UUID.randomUUID());
        channel.setNetwork("vk");
        channel.setExternalId("-999");
        channel.setStatus("active");
        rule = new AutomationRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setWorkspaceId(UUID.randomUUID());
        rule.setProjectId(UUID.randomUUID());
        rule.setCreatedBy(UUID.randomUUID());
        rule.setTargetChannelId(channel.getId());
        rule.setType("grabber");
        rule.setConfig(GrabberConfigSupport.withDefaults(java.util.Map.of()));
        row.setId(UUID.randomUUID());
        row.setContentText("hello");
        row.setAttachmentsJson(List.of());
        when(channelRepository.findByIdAndDeletedAtIsNull(channel.getId())).thenReturn(Optional.of(channel));
        when(itemRepository.findByIdForUpdate(row.getId())).thenAnswer(invocation -> Optional.of(row));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(postRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(postVariantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(publicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void mediaResolveRunsOutsideATransaction() {
        AtomicBoolean activeDuringResolve = new AtomicBoolean(true);
        when(mediaResolver.resolve(any(), any(), any(), any())).thenAnswer(invocation -> {
            activeDuringResolve.set(TransactionSynchronizationManager.isActualTransactionActive());
            return GrabberMediaResolver.Resolution.ok(List.of("photo-1_2"), false);
        });

        GrabberPostFactory.PublishResult result =
                factory.publishItem(rule, detachedItem(), rule.getCreatedBy(), null, Instant.now());

        assertThat(activeDuringResolve).isFalse();
        assertThat(result.postId()).isNotNull();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();
    }

    @Test
    void secondRetrySeesLockedPostAndDoesNotCreateAnother() {
        when(mediaResolver.resolve(any(), any(), any(), any()))
                .thenReturn(GrabberMediaResolver.Resolution.ok(List.of(), false));

        GrabberPostFactory.PublishResult first = factory.publishItem(
                rule, detachedItem(), rule.getCreatedBy(), null, Instant.parse("2026-11-23T12:00:00Z"));
        GrabberPostFactory.PublishResult second = factory.publishItem(
                rule, detachedItem(), rule.getCreatedBy(), null, Instant.parse("2026-11-23T12:00:05Z"));

        assertThat(first.alreadyPublished()).isFalse();
        assertThat(second.alreadyPublished()).isTrue();
        assertThat(second.postId()).isEqualTo(first.postId());
        verify(postRepository, times(1)).save(any());
    }

    private GrabberItemEntity detachedItem() {
        GrabberItemEntity item = new GrabberItemEntity();
        item.setId(row.getId());
        item.setContentText("hello");
        item.setAttachmentsJson(List.of());
        return item;
    }

    /** Marks the synchronization flag the way a real transaction manager does, without a database. */
    private static final class FlaggingTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        @Override
        public void rollback(TransactionStatus status) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
