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

package org.axonframework.extension.metrics.dropwizard;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricSet;
import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts the number of ingested, successful, failed and processed messages
 *
 * @author Marijn van Zelst
 * @since 3.0
 */
public class MessageCountingMonitor implements MessageMonitor<Message>, MetricSet {

    private final Counter ingestedCounter = new Counter();
    private final Counter successCounter = new Counter();
    private final Counter failureCounter = new Counter();
    private final Counter processedCounter = new Counter();
    private final Counter ignoredCounter = new Counter();

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message message) {
        ingestedCounter.inc();
        return new MessageMonitor.MonitorCallback() {
            @Override
            public void reportSuccess() {
                processedCounter.inc();
                successCounter.inc();
            }

            @Override
            public void reportFailure(Throwable cause) {
                processedCounter.inc();
                failureCounter.inc();
            }

            @Override
            public void reportIgnored() {
                ignoredCounter.inc();
            }
        };
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        Map<MetricName, Metric> metricSet = new HashMap<>();
        metricSet.put(MetricName.build("ingestedCounter"), ingestedCounter);
        metricSet.put(MetricName.build("processedCounter"), processedCounter);
        metricSet.put(MetricName.build("successCounter"), successCounter);
        metricSet.put(MetricName.build("failureCounter"), failureCounter);
        metricSet.put(MetricName.build("ignoredCounter"), ignoredCounter);
        return metricSet;
    }
}
