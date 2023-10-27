/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.CommandMessage;

import javax.annotation.Nonnull;

/**
 * Interface towards a mechanism that is capable of extracting an Aggregate Identifier and Version form a command that
 * identifies the aggregate instance the command should be invoked on.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface CommandTargetResolver {

    /**
     * Returns the Aggregate Identifier and optionally the expected version of the aggregate on which the given
     * {@code command} should be executed.
     * <p>
     * The version may be {@code null} if no specific version is required. Furthermore, the returned
     * {@link VersionedAggregateIdentifier} may be null entirely when the given {@code command} is targeted towards a
     * {@link CreationPolicy} annotated command handler that (optionally) constructs a new aggregate instance.
     *
     * @param command The command from which to extract the identifier and version.
     * @return A {@link VersionedAggregateIdentifier} instance reflecting the aggregate to execute the command on, or
     * {@code null} when the {@code command} is targeted towards a {@link CreationPolicy} annotated command handler that
     * constructs a new aggregate instance.
     * @throws IllegalArgumentException If the command is not formatted correctly to extract this information.
     * @see AggregateCreationPolicy#ALWAYS
     * @see AggregateCreationPolicy#CREATE_IF_MISSING
     */
    VersionedAggregateIdentifier resolveTarget(@Nonnull CommandMessage<?> command);
}
