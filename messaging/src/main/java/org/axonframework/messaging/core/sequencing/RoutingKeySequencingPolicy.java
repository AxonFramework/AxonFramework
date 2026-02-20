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

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@link RoutingKeySequencingPolicy} that requires sequential processing of commands targeting the same
 * {@link CommandMessage#routingKey() routing key}.
 * <p>
 * For a {@code null} or {@code empty} routing key of a {@link CommandMessage} the policy returns
 * {@link Optional#empty()}.
 * <p>
 * This policy only applies for command messages.
 *
 * @author Jakob Hatzl
 * @since 5.0.3
 */
public class RoutingKeySequencingPolicy implements SequencingPolicy<CommandMessage> {

    /**
     * Singleton instance of the {@link RoutingKeySequencingPolicy}
     */
    public static final RoutingKeySequencingPolicy INSTANCE = new RoutingKeySequencingPolicy();

    private RoutingKeySequencingPolicy() {
        // empty private singleton constructor
    }

    @Override
    public Optional<Object> sequenceIdentifierFor(@NonNull CommandMessage message,
                                                  @NonNull ProcessingContext context) {
        return message.routingKey()
                      .filter(Predicate.not(String::isEmpty))
                      .map(Object.class::cast);
    }
}
