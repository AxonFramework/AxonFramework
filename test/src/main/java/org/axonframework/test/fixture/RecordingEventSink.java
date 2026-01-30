/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An {@link EventSink} implementation recording all the events that are
 * {@link #publish(ProcessingContext, List) published}.
 * <p>
 * The recorded events can then be used to assert expectations with test cases.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class RecordingEventSink implements EventSink {

    private static final Logger logger = LoggerFactory.getLogger(RecordingEventSink.class);
    private static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofSeconds(5);

    private final List<EventMessage> recorded = new CopyOnWriteArrayList<>();
    private final Set<CompletableFuture<Void>> pendingPublishes = ConcurrentHashMap.newKeySet();
    protected final EventSink delegate;

    /**
     * Creates a new {@code RecordingEventSink} that will record all events published to the given {@code delegate}.
     *
     * @param delegate The {@link EventSink} to which events will be published.
     */
    public RecordingEventSink(@Nonnull EventSink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventSink may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        logger.debug("publish() called with {} event(s): {}",
                     events.size(),
                     events.stream().map(e -> e.payloadType().getSimpleName()).toList());
        logger.debug("publish() - delegating to {} on thread {}",
                     delegate.getClass().getSimpleName(),
                     Thread.currentThread().getName());

        CompletableFuture<Void> recordingFuture = delegate.publish(context, events)
                       .thenRun(() -> {
                           recorded.addAll(events);
                           logger.debug("publish() - thenRun completed, recorded {} event(s), total recorded: {} on thread {}",
                                        events.size(),
                                        recorded.size(),
                                        Thread.currentThread().getName());
                       });

        // Track this publish operation for synchronization
        pendingPublishes.add(recordingFuture);
        recordingFuture.whenComplete((result, error) -> {
            pendingPublishes.remove(recordingFuture);
            logger.debug("publish() - removed from pending, {} publish(es) still pending on thread {}",
                         pendingPublishes.size(),
                         Thread.currentThread().getName());
        });

        return recordingFuture;
    }

    /**
     * Awaits completion of all pending publish operations, ensuring all events are recorded.
     * <p>
     * This method should be called before accessing {@link #recorded()} when using asynchronous
     * event stores (like Axon Server) to ensure all events published during command handling
     * have been recorded.
     * <p>
     * Uses a default timeout of 5 seconds.
     */
    public void awaitPendingRecordings() {
        awaitPendingRecordings(DEFAULT_AWAIT_TIMEOUT);
    }

    /**
     * Awaits completion of all pending publish operations with a custom timeout.
     * <p>
     * This method handles the case where event publishing happens asynchronously
     * (e.g., when using Axon Server). It waits for either:
     * <ul>
     *     <li>New events to appear in the recorded list, or</li>
     *     <li>Pending publish operations to appear and complete</li>
     * </ul>
     *
     * @param timeout The maximum time to wait for pending recordings to complete.
     */
    public void awaitPendingRecordings(@Nonnull Duration timeout) {
        int initialRecordedCount = recorded.size();
        logger.debug("awaitPendingRecordings() called, {} pending publish(es), {} recorded event(s) on thread {}",
                     pendingPublishes.size(),
                     initialRecordedCount,
                     Thread.currentThread().getName());

        long deadlineMillis = System.currentTimeMillis() + timeout.toMillis();

        // If events are already recorded (arrived before we started waiting), we're done.
        // This handles the case where async events arrived between command dispatch and then() call.
        if (initialRecordedCount > 0) {
            logger.debug("awaitPendingRecordings() - events already recorded, skipping wait");
        } else if (pendingPublishes.isEmpty()) {
            // No events yet and no pending publishes - wait for async event publishing.
            // This handles the race condition where then() is called before publish() on async systems.
            waitForEventsOrPendingPublishes(deadlineMillis, initialRecordedCount);
        }

        // Now wait for any pending publishes to complete
        while (!pendingPublishes.isEmpty() && System.currentTimeMillis() < deadlineMillis) {
            // Take a snapshot of current pending futures
            CompletableFuture<?>[] pending = pendingPublishes.toArray(new CompletableFuture[0]);
            if (pending.length == 0) {
                break;
            }

            try {
                long remainingMillis = deadlineMillis - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    CompletableFuture.allOf(pending).get(remainingMillis, TimeUnit.MILLISECONDS);
                }
            } catch (TimeoutException e) {
                logger.warn("awaitPendingRecordings() - timeout waiting for {} pending publish(es)",
                           pendingPublishes.size());
                break;
            } catch (Exception e) {
                logger.debug("awaitPendingRecordings() - exception while waiting: {}",
                           e.getMessage());
                // Continue - the publish might have failed but we should still check for more
            }
        }

        logger.debug("awaitPendingRecordings() completed, {} event(s) recorded on thread {}",
                     recorded.size(),
                     Thread.currentThread().getName());
    }

    /**
     * Waits for events to be recorded or pending publishes to appear.
     * This handles the case where the command handler hasn't started publishing yet,
     * or events arrived before we started waiting.
     */
    private void waitForEventsOrPendingPublishes(long deadlineMillis, int initialRecordedCount) {
        while (System.currentTimeMillis() < deadlineMillis) {
            // Check if new events have been recorded since we started waiting
            if (recorded.size() > initialRecordedCount) {
                logger.debug("awaitPendingRecordings() - {} new event(s) recorded, total: {}",
                            recorded.size() - initialRecordedCount,
                            recorded.size());
                return;
            }

            // Check if there are pending publishes to wait for
            if (!pendingPublishes.isEmpty()) {
                logger.debug("awaitPendingRecordings() - {} pending publish(es) appeared",
                            pendingPublishes.size());
                return;
            }

            try {
                Thread.sleep(10); // Small sleep to avoid busy-waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.debug("awaitPendingRecordings() - timeout waiting for events, recorded: {}", recorded.size());
    }

    /**
     * Returns a copied list of all the {@link EventMessage EventMessages}
     * {@link #publish(ProcessingContext, List) published}.
     *
     * @return A copied list of all the {@link EventMessage EventMessages}
     * {@link #publish(ProcessingContext, List) published}.
     */
    public List<EventMessage> recorded() {
        logger.debug("recorded() called, returning {} event(s): {} on thread {}",
                     recorded.size(),
                     recorded.stream().map(e -> e.payloadType().getSimpleName()).toList(),
                     Thread.currentThread().getName());
        return List.copyOf(recorded);
    }

    /**
     * Resets this recording {@link EventSink}, by removing all recorded {@link EventMessage EventMessages}.
     *
     * @return This recording {@link EventSink}, for fluent interfacing.
     */
    public RecordingEventSink reset() {
        logger.debug("reset() called, clearing {} recorded event(s) on thread {}",
                     recorded.size(),
                     Thread.currentThread().getName());
        this.recorded.clear();
        this.pendingPublishes.clear();
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("recorded", recorded);
    }
}
