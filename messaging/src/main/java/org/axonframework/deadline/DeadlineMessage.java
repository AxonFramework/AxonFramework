/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.deadline;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * An {@link EventMessage} for a deadline, specified by its {@code deadlineName} and optionally containing a
 * {@code deadlinePayload}.
 * <p>
 * Implementations of the {@link DeadlineMessage} represent a fact (it's a specialization of {@code EventMessage}) that
 * some deadline was reached. The optional payload contains relevant data of the scheduled deadline.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3.0
 */
public interface DeadlineMessage extends EventMessage {

    /**
     * Returns the name of the {@link DeadlineMessage deadline} to be handled.
     *
     * @return The name of the {@link DeadlineMessage deadline}.
     */
    @Nonnull
    String getDeadlineName();

    @Override
    @Nonnull
    DeadlineMessage withMetaData(@Nonnull Map<String, String> metaData);

    @Override
    @Nonnull
    DeadlineMessage andMetaData(@Nonnull Map<String, String> additionalMetaData);

    @Override
    @Nonnull
    default DeadlineMessage withConvertedPayload(@Nonnull Class<?> type, @Nonnull Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    @Nonnull
    default DeadlineMessage withConvertedPayload(@Nonnull TypeReference<?> type, @Nonnull Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    @Nonnull
    DeadlineMessage withConvertedPayload(@Nonnull Type type, @Nonnull Converter converter);
}
