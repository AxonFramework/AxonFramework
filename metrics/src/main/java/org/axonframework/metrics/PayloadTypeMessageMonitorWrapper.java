/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * A {@link MessageMonitor} implementation which creates a new MessageMonitor for every {@link Message} payload type
 * ingested by it. The PayloadTypeMessageMonitorWrapper keeps track of all distinct payload types it has created
 * and only creates a new one if there is none present.
 * <p>
 * The type of MessageMonitor which is created for every payload type is configurable, as long as it implements
 * MessageMonitor and {@link MetricSet}.
 *
 * @param <T> The type of the MessageMonitor created for every payload type.Must implement both {@link MessageMonitor}
 *            and {@link MetricSet}
 * @author Steven van Beelen
 * @since 3.0
 */
public class PayloadTypeMessageMonitorWrapper<T extends MessageMonitor<Message<?>> & MetricSet>
        implements MessageMonitor<Message<?>>, MetricSet {

    private final Supplier<T> monitorSupplier;
    private final Function<Class<?>, String> monitorNameBuilder;
    private final Map<String, T> payloadTypeMonitors;
    private final Map<String, Metric> metricSet;

    /**
     * Create a PayloadTypeMessageMonitorWrapper which builds monitors through a given {@code monitorSupplier} for
     * every message payload type encountered.
     *
     * @param monitorSupplier A Supplier of MessageMonitors of type {@code T} for every encountered payload type
     */
    public PayloadTypeMessageMonitorWrapper(Supplier<T> monitorSupplier) {
        this(monitorSupplier, Class::getName);
    }

    /**
     * Create a PayloadTypeMessageMonitorWrapper which builds monitors through a given {@code monitorSupplier} for
     * every message payload type encountered and sets the monitor name as specified by the {@code monitorNameBuilder}.
     *
     * @param monitorSupplier    A Supplier of MessageMonitors of type {@code T} for every encountered payload type
     * @param monitorNameBuilder A Function where the payload type is the input (of type {@code Class<?>}) and output
     *                           is the desired name for the monitor (of type {@code String})
     */
    public PayloadTypeMessageMonitorWrapper(Supplier<T> monitorSupplier,
                                            Function<Class<?>, String> monitorNameBuilder) {
        this.monitorSupplier = monitorSupplier;
        this.monitorNameBuilder = monitorNameBuilder;
        this.payloadTypeMonitors = new ConcurrentHashMap<>();
        this.metricSet = Collections.unmodifiableMap(payloadTypeMonitors);
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        String monitorName = monitorNameBuilder.apply(message.getPayloadType());

        MessageMonitor<Message<?>> messageMonitorForPayloadType =
                payloadTypeMonitors.computeIfAbsent(monitorName, payloadType -> monitorSupplier.get());

        return messageMonitorForPayloadType.onMessageIngested(message);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Map<String, Metric> getMetrics() {
        return metricSet;
    }
}
