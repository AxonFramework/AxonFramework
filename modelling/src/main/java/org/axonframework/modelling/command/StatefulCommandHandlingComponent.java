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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;

import java.util.Set;
import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.BuilderUtils.assertNonEmpty;

/**
 * A {@link CommandHandlingComponent} implementation which allows for stateful handling of commands.
 * <p>
 * Stateful command handling is achieved by providing a {@link StateManager} which can be used to load state during
 * execution of a command.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class StatefulCommandHandlingComponent implements
        CommandHandlingComponent,
        StatefulCommandHandlerRegistry<StatefulCommandHandlingComponent>,
        DescribableComponent {

    private final String name;
    private final SimpleCommandHandlingComponent handlingComponent;
    private final StateManager stateManager;

    /**
     * Creates a new stateful command handling component with the given {@code name}.
     *
     * @param name         The name of the component, used for {@link DescribableComponent describing} the component.
     * @param stateManager The {@link StateManager} to use for loading state.
     * @return A stateful {@link CommandHandlingComponent} component with the given {@code name} and
     * {@code stateManager}.
     */
    public static StatefulCommandHandlingComponent create(@Nonnull String name, @Nonnull StateManager stateManager) {
        return new StatefulCommandHandlingComponent(name, stateManager);
    }

    private StatefulCommandHandlingComponent(@Nonnull String name, @Nonnull StateManager stateManager) {
        assertNonEmpty(name, "The name may not be null or empty");
        this.name = name;
        this.stateManager = requireNonNull(stateManager, "StateManager may not be null");
        this.handlingComponent = SimpleCommandHandlingComponent.create(name);
    }

    @Override
    public StatefulCommandHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull StatefulCommandHandler commandHandler
    ) {
        requireNonNull(name, "The name of the command handler may not be null");
        requireNonNull(commandHandler, "The command handler may not be null");

        handlingComponent.subscribe(name, ((command, context) -> {
            try {
                return commandHandler.handle(command, stateManager, context);
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        }));
        return this;
    }

    @Nonnull
    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                          @Nonnull ProcessingContext context) {
        return handlingComponent.handle(command, context);
    }

    @Override
    public StatefulCommandHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                      @Nonnull CommandHandler commandHandler) {
        handlingComponent.subscribe(name, commandHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return handlingComponent.supportedCommands();
    }


    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("handlingComponent", handlingComponent);
        descriptor.describeProperty("stateManager", stateManager);
    }
}
