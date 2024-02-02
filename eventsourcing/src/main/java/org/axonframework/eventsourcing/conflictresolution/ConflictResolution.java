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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * ParameterResolverFactory that add support for the ConflictResolver parameter type in annotated handlers.
 * <p>
 * Conflict resolution can be initialized by passing a {@link ConflictResolver} to the static {@link
 * #initialize(ConflictResolver)} method. Note that a {@link org.axonframework.messaging.unitofwork.UnitOfWork} needs
 * to be active before conflict resolution can be initialized.
 *
 * @author Rene de Waele
 */
public class ConflictResolution implements ParameterResolverFactory, ParameterResolver<ConflictResolver> {

    private static final String CONFLICT_RESOLUTION_KEY = ConflictResolution.class.getName();

    /**
     * Initialize conflict resolution in the context of the current Unit of Work dealing with a command on an event
     * sourced aggregate.
     *
     * @param conflictResolver conflict resolver able to detect conflicts
     */
    public static void initialize(ConflictResolver conflictResolver) {
        Assert.state(CurrentUnitOfWork.isStarted(), () -> "An active Unit of Work is required for conflict resolution");
        CurrentUnitOfWork.get().getOrComputeResource(CONFLICT_RESOLUTION_KEY, key -> conflictResolver);
    }

    /**
     * Returns the ConflictResolver instance that can be used to resolve conflicts of an Aggregate that has been
     * <em>previously loaded in this Unit of Work</em>. Note that, when multiple aggregates are loaded, the
     * {@link ConflictResolver} that was registered last, is returned.
     * <p>
     * Any conflicts must be resolved (by invoking one of the methods on {@link ConflictResolver}) before the Unit of
     * Work is committed.
     * <p>
     * If no Aggregate was loaded, or no conflicts are present, this method will return a {@link NoConflictResolver},
     * instance.
     *
     * @return The ConflictResolver for the last loaded Aggregate.
     */
    public static ConflictResolver getConflictResolver() {
        return CurrentUnitOfWork.map(uow -> {
            ConflictResolver conflictResolver = uow.getResource(CONFLICT_RESOLUTION_KEY);
            return conflictResolver == null ? NoConflictResolver.INSTANCE : conflictResolver;
        }).orElse(NoConflictResolver.INSTANCE);
    }

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
        if (ConflictResolver.class.equals(parameters[parameterIndex].getType())) {
            return this;
        }
        return null;
    }

    @Override
    public ConflictResolver resolveParameterValue(Message<?> message, ProcessingContext processingContext) {
        return getConflictResolver();
    }

    @Override
    public boolean matches(Message<?> message, ProcessingContext processingContext) {
        return message instanceof CommandMessage;
    }

}
