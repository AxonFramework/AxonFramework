package org.axonframework.eventhandling;

import org.axonframework.messaging.StreamableMessageSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface SegmentedEventProcessor extends EventProcessor {

    CompletableFuture<Boolean> splitSegment(int segmentId);

    String getTokenStoreIdentifier();

    CompletableFuture<Boolean> mergeSegment(int segmentId);

    void releaseSegment(int segmentId);

    void releaseSegment(int segmentId, long releaseDuration, TimeUnit unit);

    void resetTokens();

    <R> void resetTokens(R resetContext);

    void resetTokens(
            Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenSupplier);

    <R> void resetTokens(
            Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialTrackingTokenSupplier,
            R resetContext
    );

    void resetTokens(TrackingToken startPosition);

    <R> void resetTokens(TrackingToken startPosition, R resetContext);

    boolean supportsReset();

    int maxCapacity();

    Map<Integer, EventTrackerStatus> processingStatus();
}
