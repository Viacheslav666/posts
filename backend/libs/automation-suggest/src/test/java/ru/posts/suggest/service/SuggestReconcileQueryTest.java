package ru.posts.suggest.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.suggest.metrics.SuggestMetrics;

@ExtendWith(MockitoExtension.class)
class SuggestReconcileQueryTest {

    @Mock
    SuggestItemRepository itemRepository;

    @Mock
    SuggestMetrics metrics;

    @Test
    void reconcileAsksForDueActionsRatherThanEveryLinkedRow() {
        when(itemRepository.findDueForRemoteReconcile(10)).thenReturn(List.of());
        new SuggestRemoteActionReconciler(itemRepository, null, null, metrics).reconcile(10);
        verify(itemRepository).findDueForRemoteReconcile(10);
    }
}
