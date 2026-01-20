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

package org.axonframework.messaging.eventhandling.sequencing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Optional;

/**
 * SequencingPolicy that requires sequential handling of all events delivered to an event handler.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class SequentialPolicy implements SequencingPolicy {

    /**
     * Singleton instance of the {@code SequentialPolicy}.
     */
    public static final SequentialPolicy INSTANCE = new SequentialPolicy();

    /**
     * Object used to represent the full sequential policy.
     */
    @Internal
    public static final Object FULL_SEQUENTIAL_POLICY = new Object();

    private SequentialPolicy() {
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@Nonnull EventMessage task, @Nonnull ProcessingContext context) {
        return Optional.of(FULL_SEQUENTIAL_POLICY);
    }
}
