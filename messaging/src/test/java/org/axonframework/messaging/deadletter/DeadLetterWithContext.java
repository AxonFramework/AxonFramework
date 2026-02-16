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

package org.axonframework.messaging.deadletter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.Message;

/**
 * Pairs a {@link DeadLetter} with a {@link Context} carrying context resources (e.g. tracking token, aggregate info)
 * that may be needed by the dead letter queue implementation during enqueueing.
 * <p>
 * Used in tests to associate context resources with generated dead letters so that implementations like the JPA dead
 * letter queue can properly serialize context-dependent data.
 *
 * @param letter  The dead letter.
 * @param context The context with resources, or {@code null} if no context is needed.
 * @param <M>     The message type contained in the dead letter.
 * @author Mateusz Nowak
 * @since 5.1.0
 */
public record DeadLetterWithContext<M extends Message>(
        @Nonnull DeadLetter<M> letter,
        @Nullable Context context
) {

}
