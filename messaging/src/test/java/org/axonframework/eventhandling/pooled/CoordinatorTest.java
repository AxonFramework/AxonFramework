package org.axonframework.eventhandling.pooled;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.*;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.eventhandling.Segment.computeSegment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link Coordinator}.
 *
 * @author Fabio Couto
 */
class CoordinatorTest {

    private static final String PROCESSOR_NAME = "test";

    private Coordinator testSubject;

    private final Segment segment = computeSegment(0);
    private final List<Segment> segments = singletonList(segment);

    private final TokenStore tokenStore = mock(TokenStore.class);
    private final WorkPackage workPackage = mock(WorkPackage.class);
    private final ScheduledThreadPoolExecutor executorService = mock(ScheduledThreadPoolExecutor.class);
    @SuppressWarnings("unchecked")
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource = mock(StreamableMessageSource.class);

    @BeforeEach
    void setUp() {
        testSubject = Coordinator.builder()
                                 .name(PROCESSOR_NAME)
                                 .tokenStore(tokenStore)
                                 .workPackageFactory((segment, trackingToken) -> workPackage)
                                 .maxClaimedSegments(segments.size())
                                 .transactionManager(NoTransactionManager.instance())
                                 .executorService(executorService)
                                 .messageSource(messageSource)
                                 .build();
    }

    @Test
    void testIfNewCoordinationTaskCanBeSchedulledAfterTokenReleaseClaimFails() {
        //arrange
        final RuntimeException streamOpenException = new RuntimeException("Some exception during event stream open");
        final RuntimeException releaseClaimException = new RuntimeException("Some exception during release claim");
        final GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(0);

        doReturn(segments).when(tokenStore).fetchAvailableSegments(PROCESSOR_NAME);
        doReturn(token).when(tokenStore).fetchToken(eq(PROCESSOR_NAME), anyInt());
        doThrow(releaseClaimException).when(tokenStore).releaseClaim(eq(PROCESSOR_NAME), anyInt());
        doThrow(streamOpenException).when(messageSource).openStream(any());
        doReturn(completedFuture(releaseClaimException)).when(workPackage).abort(any());
        doReturn(segment).when(workPackage).segment();
        doAnswer(runTask()).when(executorService).submit(any(Runnable.class));

        //act
        testSubject.start();

        //asserts
        verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    private Answer<Future<Integer>> runTask() {
        return invocationOnMock -> {
            final Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return completedFuture(0);
        };
    }
}
