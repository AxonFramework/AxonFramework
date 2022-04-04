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

package org.axonframework.micrometer;

import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * A {@link MessageMonitor} implementation which creates a new MessageMonitor for every {@link Message} payload type
 * ingested by it. The PayloadTypeMessageMonitorWrapper keeps track of all distinct payload types it has created
 * and only creates a new one if there is none present.
 * <p>
 * The type of MessageMonitor which is created for every payload type is configurable, as long as it implements
 * MessageMonitor}.
 *
 * @param <T> The type of the MessageMonitor created for every payload type.Must implement both {@link MessageMonitor}
 * @author Steven van Beelen
 * @author Marijn van Zelst
 * @since 4.1
 * @deprecated As of release 4.4, replaced by using {@link Tag}s on the monitor implementations.
 * Use {@link org.axonframework.micrometer.GlobalMetricRegistry#registerWithConfigurerWithDefaultTags(Configurer) to achieve the same behavior as this implementation.
 */
@Deprecated
public class PayloadTypeMessageMonitorWrapper<T extends MessageMonitor<Message<?>>>
        implements MessageMonitor<Message<?>> {

    private final Function<String, T> monitorSupplier;
    private final Map<String, T> payloadTypeMonitors;
    private final Function<Class<?>, String> monitorNameBuilder;

    /**
     * Create a PayloadTypeMessageMonitorWrapper which builds monitors through a given {@code monitorSupplier} for
     * every message payload type encountered.
     *
     * @param monitorSupplier A Supplier of MessageMonitors of type {@code T} for every encountered payload type
     */
    public PayloadTypeMessageMonitorWrapper(Function<String, T> monitorSupplier) {
        this(monitorSupplier, Class::getName);
    }

    /**
     * Create a PayloadTypeMessageMonitorWrapper which builds monitors through a given {@code monitorSupplier} for
     * every message payload type encountered and sets the monitor name as specified by the {@code monitorNameBuilder}.
     *
     * @param monitorSupplier    A Function to create a MessageMonitor of type {@code T}, with the given {@code String}
     *                           as name, for every encountered payload type
     * @param monitorNameBuilder A Function where the payload type is the input (of type {@code Class<?>}) and output
     *                           is the desired name for the monitor (of type {@code String})
     */
    public PayloadTypeMessageMonitorWrapper(Function<String, T> monitorSupplier,
                                            Function<Class<?>, String> monitorNameBuilder) {
        this.monitorSupplier = monitorSupplier;
        this.payloadTypeMonitors = new ConcurrentHashMap<>();
        this.monitorNameBuilder = monitorNameBuilder;
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        String monitorName = monitorNameBuilder.apply(message.getPayloadType());

        MessageMonitor<Message<?>> messageMonitorForPayloadType =
                payloadTypeMonitors.computeIfAbsent(monitorName, monitorSupplier);

        return messageMonitorForPayloadType.onMessageIngested(message);
    }
}
