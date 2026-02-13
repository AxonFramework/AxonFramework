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
 * {@link SequencingPolicy} that imposes no sequencing at all on the processing of commands. Infrastructure components
 * may decide upon this sequencing policy being present bypassing sequencing infrastructure at all.
 *
 * @author Jakob Hatzl
 * @since 5.0.3
 */
public class NoOpSequencingPolicy implements SequencingPolicy {

    /**
     * Singleton instance of the {@link NoOpSequencingPolicy}
     */
    public static final NoOpSequencingPolicy INSTANCE = new NoOpSequencingPolicy();

    private NoOpSequencingPolicy() {
        // empty private singleton constructor
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@NonNull Message message,
                                                     @NonNull ProcessingContext context) {
        return Optional.empty();
    }
}
