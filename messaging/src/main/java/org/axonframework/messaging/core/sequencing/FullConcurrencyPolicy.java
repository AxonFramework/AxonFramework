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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Optional;

/**
 * SequencingPolicy that does not enforce any sequencing requirements on message processing.
 *
 * @author Allard Buijze
 * @author Henrique Sena
 * @since 0.3
 */
public class FullConcurrencyPolicy implements SequencingPolicy<Message> {

    /**
     * Singleton instance of the {@code FullConcurrencyPolicy}.
     */
    public static final FullConcurrencyPolicy INSTANCE = new FullConcurrencyPolicy();

    private FullConcurrencyPolicy() {
        // empty private singleton constructor
    }

    @Override
    public Optional<Object> sequenceIdentifierFor(@Nonnull Message message, @Nonnull ProcessingContext context) {
        return Optional.of(message.identifier());
    }
}
