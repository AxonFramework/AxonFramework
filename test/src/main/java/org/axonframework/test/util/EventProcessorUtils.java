/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.test.util;

import jakarta.annotation.Nonnull;
import org.awaitility.Awaitility;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.time.Duration;
import java.util.Map;

/**
 * Utility class providing helper methods for waiting on event processors during testing.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
public class EventProcessorUtils {

    /**
     * Waits for all streaming event processors to catch up with the latest events in the event store.
     * This method compares each processor's current token position against the head token from the event store.
     * <p>
     * The waiting mechanism polls every 50ms and checks if all processor segments have reached or passed
     * the head position. This ensures deterministic query testing when read models are updated by
     * asynchronous event processors.
     *
     * @param configuration The Axon configuration containing the event processors and event store.
     * @param timeout       The maximum time to wait for processors to catch up.
     * @throws RuntimeException if the timeout is exceeded before all processors catch up.
     */
    public static void waitForEventProcessorsToCatchUp(@Nonnull AxonConfiguration configuration,
                                                 @Nonnull Duration timeout) {
        // Get all streaming event processors from the configuration
        Map<String, StreamingEventProcessor> eventProcessors =
                configuration.getComponents(StreamingEventProcessor.class);

        if (eventProcessors.isEmpty()) {
            // No streaming processors configured, no need to wait
            return;
        }

        // Get the event store to obtain the latest token
        EventStore eventStore = configuration.getComponent(EventStore.class);

        try {
            Awaitility.await()
                    .atMost(timeout)
                    .pollInterval(Duration.ofMillis(50))
                    .untilAsserted(() -> {
                        // Get the latest token from the event store (head position)
                        TrackingToken headToken = eventStore.latestToken(null).join();

                        // Check if all processors have caught up to the head position
                        for (var processor : eventProcessors.values()) {
                            var processingStatus = processor.processingStatus();

                            for (var status : processingStatus.values()) {
                                TrackingToken currentToken = status.getTrackingToken();

                                // If processor hasn't started yet or hasn't caught up
                                if (currentToken == null || !currentToken.covers(headToken)) {
                                    throw new AssertionError(
                                            "Event processor '" + processor.name() +
                                            "' has not caught up yet. Current token: " + currentToken +
                                            ", head token: " + headToken
                                    );
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(
                    "Timeout waiting for event processors to catch up. This may indicate that " +
                    "event processing is slower than expected, or that events are not being processed. " +
                    "Consider increasing the timeout or checking your event processor configuration.",
                    e
            );
        }
    }

    private EventProcessorUtils() {
        // Utility class
    }
}
