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

import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A {@link CommandHandlingComponent} implementation which allows for stateful handling of commands.
 * <p>
 * The component uses a {@link ModelRegistry} which provides access to the models that are used to
 * handle the commands. The models are resolved through the {@link ModelContainer} which is provided to the
 * {@link StatefulCommandHandler} when handling a command.
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
    private final ModelRegistry modelRegistry;

    /**
     * Initializes a new stateful command handling component with the given {@code name}.
     *
     * @param name          The name of the component, used for describing it to the {@link DescribableComponent}
     * @param modelRegistry The model registry to use for resolving the {@link ModelContainer}
     */
    private StatefulCommandHandlingComponent(String name, ModelRegistry modelRegistry) {
        this.name = name;
        this.handlingComponent = SimpleCommandHandlingComponent.create(name);
        this.modelRegistry = modelRegistry;
    }

    /**
     * Creates a new stateful command handling component with the given {@code name}.
     * @param name          The name of the component, used for describing it to the {@link DescribableComponent}
     * @param modelRegistry The model registry to use for resolving the {@link ModelContainer}
     * @return A stateful {@link CommandHandlingComponent} component with the given {@code name} and {@code modelRegistry}
     */
    public static StatefulCommandHandlingComponent create(String name, ModelRegistry modelRegistry) {
        return new StatefulCommandHandlingComponent(name, modelRegistry);
    }


    @Override
    public StatefulCommandHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull StatefulCommandHandler commandHandler
    ) {
        handlingComponent.subscribe(name, ((command, context) -> {
            try {
                var modelContainer = modelRegistry.modelContainer(context);
                return commandHandler.handle(command, modelContainer, context);
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
        descriptor.describeProperty("modelRegistry", modelRegistry);
    }

}
