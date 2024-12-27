/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.eventhandling.EventMessage;

import java.util.Map;

/**
 * An {@link EventMessage} for a deadline, specified by its {@code deadlineName} and optionally containing a
 * {@code deadlinePayload}.
 * <p>
 * Implementations of the {@link DeadlineMessage} represent a fact (it's a specialization of {@code EventMessage}) that
 * some deadline was reached. The optional payload contains relevant data of the scheduled deadline.
 *
 * @param <P> The type of {@link #getPayload() payload} contained in this {@link DeadlineMessage}. May be {@link Void}
 *            if no payload was provided.
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3.0
 */
public interface DeadlineMessage<P> extends EventMessage<P> {

    /**
     * Returns the name of the {@link DeadlineMessage deadline} to be handled.
     *
     * @return The name of the {@link DeadlineMessage deadline}.
     */
    String getDeadlineName();

    @Override
    DeadlineMessage<P> withMetaData(@Nonnull Map<String, ?> metaData);

    @Override
    DeadlineMessage<P> andMetaData(@Nonnull Map<String, ?> additionalMetaData);
}
