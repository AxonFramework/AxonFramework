/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Objects;

/**
 * A {@link CommandDispatcher} that publishes commands to a {@link CommandGateway} in a predefined
 * {@link ProcessingContext}.
 * <p>
 * Any commands dispatched through this {@code CommandDispatcher} occur within the {@code context} this dispatcher was
 * created with. You can construct one through the
 * {@link CommandDispatcher#forContext(ProcessingContext, Configuration)} method.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class ContextAwareCommandDispatcher implements CommandDispatcher {

    private final CommandGateway commandGateway;
    private final ProcessingContext context;

    ContextAwareCommandDispatcher(@Nonnull CommandGateway commandGateway,
                                  @Nonnull ProcessingContext context) {
        this.commandGateway = Objects.requireNonNull(commandGateway, "The Command Gateway must not be null.");
        this.context = Objects.requireNonNull(context, "The Processing Context must not be null.");
    }

    @Override
    public CommandResult send(@Nonnull Object command) {
        return commandGateway.send(command, context);
    }

    @Override
    public CommandResult send(@Nonnull Object command, @Nonnull Metadata metadata) {
        return commandGateway.send(command, metadata, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("processingContext", context);
        descriptor.describeProperty("commandGateway", commandGateway);
    }
}
