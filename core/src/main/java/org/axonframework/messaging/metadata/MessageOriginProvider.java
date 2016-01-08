package org.axonframework.messaging.metadata;

import org.axonframework.messaging.Message;

import java.util.Collections;
import java.util.Map;

/**
 * CorrelationDataProvider that provides the {@link Message#getIdentifier() identifier} of a {@link Message} to
 * other messages that are created as result of processing the first message.
 *
 * @author Rene de Waele
 */
public enum MessageOriginProvider implements CorrelationDataProvider {
    INSTANCE;

    /**
     * The default meta-data key, which is used to identify the {@link Message} that triggered another message
     */
    public static final String DEFAULT_CORRELATION_KEY = "message-origin";

    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        return Collections.singletonMap(DEFAULT_CORRELATION_KEY, message.getIdentifier());
    }
}
