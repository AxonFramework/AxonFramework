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

package org.axonframework.messaging.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.messaging.core.ResultMessage;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link ResultMessage} implementation that holds incremental update of a subscription query.
 *
 * @author Milan Savic
 * @since 3.3.0
 */
public interface SubscriptionQueryUpdateMessage extends QueryResponseMessage {

    @Override
    @Nonnull
    SubscriptionQueryUpdateMessage withMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    SubscriptionQueryUpdateMessage andMetadata(@Nonnull Map<String, String> metadata);

    @Override
    @Nonnull
    default SubscriptionQueryUpdateMessage withConvertedPayload(@Nonnull Class<?> type,
                                                                       @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default SubscriptionQueryUpdateMessage withConvertedPayload(@Nonnull TypeReference<?> type,
                                                                       @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    SubscriptionQueryUpdateMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
