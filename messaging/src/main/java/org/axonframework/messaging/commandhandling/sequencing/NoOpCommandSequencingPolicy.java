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

package org.axonframework.messaging.commandhandling.sequencing;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * {@link CommandSequencingPolicy} that imposes no sequencing at all on the processing of commands.
 *
 * @author Jakob Hatzl
 * @since 5.0.3
 */
public class NoOpCommandSequencingPolicy implements CommandSequencingPolicy {

    /**
     * Singleton instance of the {@link NoOpCommandSequencingPolicy}
     */
    public static final NoOpCommandSequencingPolicy INSTANCE = new NoOpCommandSequencingPolicy();

    private NoOpCommandSequencingPolicy() {
        // empty private singleton constructor
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@NonNull CommandMessage command,
                                                     @NonNull ProcessingContext context) {
        return Optional.empty();
    }
}
