/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * Counts the number of ingested, successful, failed and processed messages
 *
 * @author Marijn van Zelst
 * @author Ivan Dugalic
 * @since 4.1
 */
public class MessageCountingMonitor implements MessageMonitor<Message<?>> {

    private static final String INGESTED_COUNTER = ".ingestedCounter";
    private static final String SUCCESS_COUNTER = ".successCounter";
    private static final String FAILURE_COUNTER = ".failureCounter";
    private static final String PROCESSED_COUNTER = ".processedCounter";
    private static final String IGNORED_COUNTER = ".ignoredCounter";

    private final String meterNamePrefix;
    private final MeterRegistry meterRegistry;
    private final Function<Message<?>, Iterable<Tag>> tagsBuilder;

    private MessageCountingMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {

        this(meterNamePrefix,
             meterRegistry,
             message -> Tags.empty());
    }


    private MessageCountingMonitor(String meterNamePrefix, MeterRegistry meterRegistry,
                                   Function<Message<?>, Iterable<Tag>> tagsBuilder) {
        this.meterNamePrefix = meterNamePrefix;
        this.meterRegistry = meterRegistry;
        this.tagsBuilder = tagsBuilder;
    }

    /**
     * Creates a message counting monitor
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @return The message counting monitor
     */
    public static MessageCountingMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {

        return new MessageCountingMonitor(meterNamePrefix, meterRegistry);
    }

    /**
     * Creates a message counting monitor
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @param tagsBuilder     The function used to construct the list of micrometer {@link Tag}, based on the ingested
     *                        message
     * @return The message counting monitor
     */
    public static MessageCountingMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry,
                                                      Function<Message<?>, Iterable<Tag>> tagsBuilder) {

        return new MessageCountingMonitor(meterNamePrefix, meterRegistry, tagsBuilder);
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {

        Iterable<Tag> tags = tagsBuilder.apply(message);
        Counter ingestedCounter = meterRegistry.counter(meterNamePrefix + INGESTED_COUNTER, tags);
        Counter successCounter = meterRegistry.counter(meterNamePrefix + SUCCESS_COUNTER, tags);
        Counter failureCounter = meterRegistry.counter(meterNamePrefix + FAILURE_COUNTER, tags);
        Counter processedCounter = meterRegistry.counter(meterNamePrefix + PROCESSED_COUNTER, tags);
        Counter ignoredCounter = meterRegistry.counter(meterNamePrefix + IGNORED_COUNTER, tags);

        ingestedCounter.increment();

        return new MonitorCallback() {
            @Override
            public void reportSuccess() {
                processedCounter.increment();
                successCounter.increment();
            }

            @Override
            public void reportFailure(Throwable cause) {
                processedCounter.increment();
                failureCounter.increment();
            }

            @Override
            public void reportIgnored() {
                ignoredCounter.increment();
            }
        };
    }
}
