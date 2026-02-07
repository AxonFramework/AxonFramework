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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Optional;

/**
 * Interface to a policy definition for concurrent command processing.
 * <p>
 * Some implementations are provided by default: <ul>
 * <li>{@link RoutingKeyCommandSequencingPolicy}: Forces all commands targeting the same
 * {@link CommandMessage#routingKey() routing key} to be handled sequentially.</li>
 * <li>{@link NoOpCommandSequencingPolicy}: Imposes no sequencing on commands at all.</li>
 * </ul>
 *
 * @author Jakob Hatzl
 * @since 5.0.3
 */
@FunctionalInterface
public interface CommandSequencingPolicy {

    /**
     * Returns the sequence identifier for the given {@code command}. When two commands have the same identifier (as
     * defined by their equals method), they will be executed sequentially. A {@code Optional#empty()} value indicates
     * that there are no sequencing requirements for the handling of this command.
     * <p>
     * The {@code Optional#empty()} should ONLY be returned when the policy cannot determine a sequence identifier for
     * the given event. This typically happens when the policy is not applicable for the specific command type. When
     * {@code Optional#empty()} is returned, it is up to the component using this policy to provide a default behavior,
     * use another policy, or throw an exception / react in any other way - as appropriate.
     *
     * @param command The command for which to get the sequencing identifier.
     * @param context The processing context in which the command is being handled.
     * @return A sequence identifier for the given command, or {@code Optional#empty()} if this policy cannot determine
     * a sequence identifier for the given command.
     */
    Optional<Object> getSequenceIdentifierFor(@Nonnull CommandMessage command, @Nonnull ProcessingContext context);
}
