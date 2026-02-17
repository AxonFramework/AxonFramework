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

package org.axonframework.messaging.core.sequencing;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * {@link SequencingPolicy} that imposes no sequencing at all on the processing of messages. Infrastructure components
 * may decide upon this sequencing policy being present bypassing sequencing infrastructure at all.
 *
 * @param <M> the type of message to sequence
 * @author Jakob Hatzl
 * @since 5.0.3
 */
public class NoOpSequencingPolicy<M extends Message> implements SequencingPolicy<M> {

    /**
     * Singleton instance of the {@link NoOpSequencingPolicy}
     */
    private static final NoOpSequencingPolicy<? extends Message> INSTANCE = new NoOpSequencingPolicy<>();

    /**
     * Get a singleton instance of the {@code NoOpSequencingPolicy}.
     *
     * @param <T> the type of message to sequence
     * @return the {@code NoOpSequencingPolicy} singleton instance
     */
    public static <T extends Message> NoOpSequencingPolicy<T> instance() {
        //noinspection unchecked
        return (NoOpSequencingPolicy<T>) INSTANCE;
    }

    private NoOpSequencingPolicy() {
        // empty private singleton constructor
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@NonNull M message,
                                                     @NonNull ProcessingContext context) {
        return Optional.empty();
    }
}
