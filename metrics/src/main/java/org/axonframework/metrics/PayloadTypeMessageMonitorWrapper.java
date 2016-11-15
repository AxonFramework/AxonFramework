package org.axonframework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * A {@link MessageMonitor} implementation which creates a new MessageMonitor for every {@link Message} payload type
 * ingested by it. The PayloadTypeMessageMonitorWrapper keeps track of all distinct payload types it has created
 * and only creates a new one if there is none present.
 *
 * The type of MessageMonitor which is created for every payload type is configurable, as long as it implements
 * MessageMonitor and {@link MetricSet}.
 *
 * @param <T> The type of the MessageMonitor created for every payload type.
 *           Required to extends MessageMonitor and MetricSet
 */
public class PayloadTypeMessageMonitorWrapper<T extends MessageMonitor<Message<?>> & MetricSet> implements MessageMonitor<Message<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadTypeMessageMonitorWrapper.class);

    private final Class<T> monitorClass;
    private Map<Class<?>, MessageMonitor<Message<?>>> payloadTypeMonitors;

    private final MetricRegistry metricRegistry;
    private final String metricNamePrepend;

    /**
     * Create a PayloadTypeMessageMonitorWrapper which builds monitors based on the given {@code monitorClass},
     * registers them to the given {@code metricRegistry} and prepend the metric names with the given
     * {@code metricNamePrepend}.
     *
     * @param monitorClass the MessageMonitor Class used to instantiate a monitor per payload type
     * @param metricRegistry the MetricRegistry where newly created monitors will be registered to
     * @param metricNamePrepend a String used as a prepend for the metric metricNamePrepend given upon registration to the MetricRegistry
     */
    public PayloadTypeMessageMonitorWrapper(Class<T> monitorClass, MetricRegistry metricRegistry, String metricNamePrepend) {
        this.monitorClass = monitorClass;
        this.payloadTypeMonitors = new HashMap<>();

        this.metricRegistry = metricRegistry;
        this.metricNamePrepend = metricNamePrepend;
    }

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
        Class<?> messagePayloadType = message.getPayloadType();

        MessageMonitor<Message<?>> messageMonitorForPayloadType =
                payloadTypeMonitors.computeIfAbsent(messagePayloadType, payloadType -> {
                    try {
                        MessageMonitor<Message<?>> newMessageMonitor = monitorClass.newInstance();
                        registerMessageMonitor((Metric) newMessageMonitor, metricNamePrepend, payloadType);
                        return newMessageMonitor;
                    } catch (InstantiationException | IllegalAccessException e) {
                        String errorMessage = "Failed to create a MessageMonitor " +
                                "of type [" + monitorClass.getSimpleName() + "] " +
                                "for message payload type [" + payloadType.getSimpleName() + "]";
                        LOGGER.error(errorMessage);
                        throw new IllegalArgumentException(errorMessage, e);
                    }
                });

        return messageMonitorForPayloadType.onMessageIngested(message);
    }

    private void registerMessageMonitor(Metric messageMonitor, String metricNamePrepend, Class<?> payloadType) {
        try {
            String metricName = name(metricNamePrepend, this.getClass().getSimpleName(), monitorClass.getSimpleName(),
                    payloadType.getSimpleName());
            metricRegistry.register(metricName, messageMonitor);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Failed to register metric [{}] for payload type [{}]",
                    monitorClass.getSimpleName(), payloadType.getSimpleName(), e);
        }
    }

}
