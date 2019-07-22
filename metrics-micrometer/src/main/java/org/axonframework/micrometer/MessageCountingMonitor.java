/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

/**
 * Counts the number of ingested, successful, failed and processed messages
 *
 * @author Marijn van Zelst
 * @since 4.1
 */
public class MessageCountingMonitor implements MessageMonitor<Message<?>> {

    private final Counter ingestedCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter processedCounter;
    private final Counter ignoredCounter;

    private MessageCountingMonitor(Counter ingestedCounter, Counter successCounter, Counter failureCounter,
                                   Counter processedCounter, Counter ignoredCounter) {
        this.ingestedCounter = ingestedCounter;
        this.successCounter = successCounter;
        this.failureCounter = failureCounter;
        this.processedCounter = processedCounter;
        this.ignoredCounter = ignoredCounter;
    }

    /**
     * Creates a message counting monitor
     *
     * @param meterNamePrefix The prefix for the meter name that will be created in the given meterRegistry
     * @param meterRegistry   The meter registry used to create and register the meters
     * @return the message counting monitor
     */
    public static MessageCountingMonitor buildMonitor(String meterNamePrefix, MeterRegistry meterRegistry) {
        Counter ingestedCounter = meterRegistry.counter(meterNamePrefix + ".ingestedCounter");
        Counter successCounter = meterRegistry.counter(meterNamePrefix + ".successCounter");
        Counter failureCounter = meterRegistry.counter(meterNamePrefix + ".failureCounter");
        Counter processedCounter = meterRegistry.counter(meterNamePrefix + ".processedCounter");
        Counter ignoredCounter = meterRegistry.counter(meterNamePrefix + ".ignoredCounter");

        return new MessageCountingMonitor(ingestedCounter,
                                          successCounter,
                                          failureCounter,
                                          processedCounter,
                                          ignoredCounter);
    }

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
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
