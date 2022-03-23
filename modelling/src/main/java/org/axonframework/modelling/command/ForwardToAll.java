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

package org.axonframework.modelling.command;

import org.axonframework.messaging.Message;

import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * Forward all messages {@code T} regardless of their set up.
 *
 * @param <T> the implementation {@code T} of the {@link org.axonframework.messaging.Message} being filtered.
 * @author Steven van Beelen
 * @since 3.1
 */
public class ForwardToAll<T extends Message<?>> implements ForwardingMode<T> {

    @Override
    public <E> Stream<E> filterCandidates(@Nonnull T message, @Nonnull Stream<E> candidates) {
        return candidates;
    }
}
