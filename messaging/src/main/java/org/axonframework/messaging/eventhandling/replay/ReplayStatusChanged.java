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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.common.TypeReference;
import org.axonframework.common.annotation.Internal;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.core.Message;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link Message} signalling that the {@link ReplayStatus} of an Event Handling Component changed.
 *
 * @author Simon Zambrovski
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 5.1.0
 */
@Internal
public interface ReplayStatusChanged extends Message {

    /**
     * The status changed to as notified by this message.
     *
     * @return the status changed to as notified by this message
     */
    ReplayStatus status();

    @Override
    ReplayStatusChanged withMetadata(Map<String, String> metadata);

    @Override
    ReplayStatusChanged andMetadata(Map<String, @Nullable String> metadata);

    @Override
    default ReplayStatusChanged withConvertedPayload(Class<?> type, Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    default ReplayStatusChanged withConvertedPayload(TypeReference<?> type, Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    ReplayStatusChanged withConvertedPayload(Type type, Converter converter);
}
