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
import java.util.function.Predicate;

/**
 * {@link CommandSequencingPolicy} that requires sequential processing of commands targeting the same
 * {@link CommandMessage#routingKey() routing key}.
 * <p>
 * For a {@code null} or {@code empty} routing key of a {@link CommandMessage} the policy returns
 * {@link Optional#empty()}.
 *
 * @author Jakob Hatzl
 * @since 5.0.3
 */
public class RoutingKeyCommandSequencingPolicy implements CommandSequencingPolicy {

    /**
     * Singleton instance of the {@link RoutingKeyCommandSequencingPolicy}
     */
    public static final RoutingKeyCommandSequencingPolicy INSTANCE = new RoutingKeyCommandSequencingPolicy();

    private RoutingKeyCommandSequencingPolicy() {
        // empty private singleton constructor
    }

    @Override
    public Optional<Object> getSequenceIdentifierFor(@NonNull CommandMessage command,
                                                     @NonNull ProcessingContext context) {
        return command.routingKey()
                      .filter(Predicate.not(String::isEmpty))
                      .map(Object.class::cast);
    }
}
