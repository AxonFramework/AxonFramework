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

package org.axonframework.integrationtests.testsuite.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


import org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.replay.ReplayStatusChanged;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.axonframework.messaging.eventhandling.replay.annotation.ReplayStatusChangedHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.junit.jupiter.api.*;

/**
 * Test class validating that
 * {@link org.axonframework.messaging.eventhandling.replay.ReplayStatusChangedHandler ReplayStatusChangeHandlers} are
 * invoked when a {@link org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken} is about to
 * {@link org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken#willFinish(TrackingToken)
 * finish}.
 *
 * @author Simon Zambrovski
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 5.1.0
 */
class ReplayStatusChangedHandlerIT extends AbstractStudentIT {

    private static final String PSEP_NAME = "replayStatusChangeHandler";

    static AtomicInteger eventHandlerInvocations = new AtomicInteger(0);
    static AtomicBoolean resetHandlerInvoked = new AtomicBoolean(false);
    static AtomicBoolean replayStatusChangedHandlerInvoked = new AtomicBoolean(false);

    // TODO expand test to trigger on start of replay as well
    @Test
    void resettingPsepTriggersReplayStatusChangeHandlersWhenFinishingTheReplay() {
        // given
        startApp();
        Map<String, EventProcessor> eventProcessors = startedConfiguration.getComponents(EventProcessor.class);
        assertThat(eventProcessors).containsKey(PSEP_NAME);
        PooledStreamingEventProcessor psep = (PooledStreamingEventProcessor) eventProcessors.get(PSEP_NAME);

        // when
        var studentId = UUID.randomUUID().toString();
        studentEnrolledToCourse(studentId, "my-courseId-1");
        studentEnrolledToCourse(studentId, "my-courseId-2");
        studentEnrolledToCourse(studentId, "my-courseId-3");
        studentEnrolledToCourse(studentId, "my-courseId-4");

        // then
        await().atMost(Duration.ofMillis(5000))
               .untilAsserted(() -> assertThat(eventHandlerInvocations).hasValue(4));
        assertThat(resetHandlerInvoked).isFalse();
        assertThat(replayStatusChangedHandlerInvoked).isFalse();

        // when
        eventHandlerInvocations.set(0);
        psep.shutdown()
            .thenCompose(ignored -> psep.resetTokens())
            .thenRun(() -> {
                for (int i = 5; i < 105; i++) {
                    studentEnrolledToCourse(studentId, "my-courseId-" + i);
                }
            })
            .thenCompose(ignored -> psep.start())
            .join();

        // then
        await().atMost(Duration.ofMillis(5000))
               .untilAsserted(() -> assertThat(eventHandlerInvocations).hasValue(104));
        assertThat(resetHandlerInvoked).isTrue();
        assertThat(replayStatusChangedHandlerInvoked).isTrue();
    }

    @SuppressWarnings("unused")
    static class Projector {

        @EventHandler
        public void on(StudentEnrolledEvent event, TrackingToken token) {
            eventHandlerInvocations.incrementAndGet();
        }

        @ResetHandler
        public void reset(ResetContext resetContext) {
            resetHandlerInvoked.set(true);
        }

        @ReplayStatusChangedHandler
        public void on(ReplayStatusChanged context) {
            replayStatusChangedHandlerInvoked.set(true);
        }
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        return configureProcessorWithAnnotatedEventHandlingComponent(configurer);
    }

    private static EventSourcingConfigurer configureProcessorWithAnnotatedEventHandlingComponent(
            EventSourcingConfigurer configurer
    ) {
        var studentRegisteredCoursesProcessor = EventProcessorModule
                .pooledStreaming(PSEP_NAME)
                .eventHandlingComponents(components -> components.autodetected(
                        cfg -> new Projector()
                ))
                .customized(
                        (config, psep) -> psep.initialSegmentCount(2)
                                              .batchSize(1)
                );
        return configurer.messaging(
                messaging -> messaging.eventProcessing(
                        ep -> ep.pooledStreaming(
                                ps -> ps.processor(studentRegisteredCoursesProcessor)
                        )
                )
        ).componentRegistry(cr -> cr.disableEnhancer(AxonServerConfigurationEnhancer.class));
    }
}
