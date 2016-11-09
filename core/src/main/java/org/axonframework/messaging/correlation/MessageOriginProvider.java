/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.messaging.correlation;

import org.axonframework.messaging.Message;

import java.util.Collections;
import java.util.Map;

/**
 * CorrelationDataProvider that provides the {@link Message#getIdentifier() identifier} of a {@link Message} to
 * other messages that are created as result of processing the first message.
 *
 * @author Rene de Waele
 */
public class MessageOriginProvider implements CorrelationDataProvider {
    private static final String DEFAULT_CORRELATION_KEY = "message-origin";

    /**
     * Returns the default meta-data key for the message origin.
     *
     * @return the default meta-data key for the message origin
     */
    public static String getDefaultCorrelationKey() {
        return DEFAULT_CORRELATION_KEY;
    }

    private final String correlationKey;

    /**
     * Initializes a {@link MessageOriginProvider} that uses the default key {@link #getDefaultCorrelationKey()}.
     */
    public MessageOriginProvider() {
        this(DEFAULT_CORRELATION_KEY);
    }

    /**
     * Initializes a {@link MessageOriginProvider} that uses the given {@code correlationKey}.
     *
     * @param correlationKey the key used to store the origin message identifier in the metadata of another message
     */
    public MessageOriginProvider(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    @Override
    public Map<String, ?> correlationDataFor(Message<?> message) {
        return Collections.singletonMap(correlationKey, message.getIdentifier());
    }

    /**
     * Returns the meta-data key for the message origin, which is used to identify the {@link Message} that triggered
     * another message.
     *
     * @return he meta-data key for the message origin
     */
    public String getCorrelationKey() {
        return correlationKey;
    }

}
