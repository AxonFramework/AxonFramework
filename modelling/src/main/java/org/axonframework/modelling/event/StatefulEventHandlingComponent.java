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

package org.axonframework.modelling.event;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;

import java.util.Set;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.BuilderUtils.assertNonEmpty;

/**
 * An {@link EventHandlingComponent} implementation which allows for stateful handling of events.
 * <p>
 * Stateful event handling is achieved by providing a {@link StateManager} which can be used to load state during
 * execution of an event.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class StatefulEventHandlingComponent implements
        EventHandlingComponent,
        StatefulEventHandlerRegistry<StatefulEventHandlingComponent>,
        DescribableComponent {

    private final String name;
    private final SimpleEventHandlingComponent handlingComponent;
    private final StateManager stateManager;

    /**
     * Creates a new stateful event handling component with the given {@code name}.
     *
     * @param name         The name of the component, used for {@link DescribableComponent describing} the component.
     * @param stateManager The {@link StateManager} to use for loading state.
     * @return A stateful {@link EventHandlingComponent} component with the given {@code name} and
     * {@code stateManager}.
     */
    public static StatefulEventHandlingComponent create(@Nonnull String name, @Nonnull StateManager stateManager) {
        return new StatefulEventHandlingComponent(name, stateManager);
    }

    private StatefulEventHandlingComponent(@Nonnull String name, @Nonnull StateManager stateManager) {
        assertNonEmpty(name, "The name may not be null or empty");
        this.name = name;
        this.stateManager = requireNonNull(stateManager, "StateManager may not be null");
        this.handlingComponent = new SimpleEventHandlingComponent();
    }

    @Override
    public StatefulEventHandlingComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull StatefulEventHandler eventHandler
    ) {
        requireNonNull(name, "The name of the event handler may not be null");
        requireNonNull(eventHandler, "The event handler may not be null");

        handlingComponent.subscribe(name, ((event, context) -> {
            try {
                return eventHandler.handle(event, stateManager, context);
            } catch (Throwable e) {
                return MessageStream.failed(e);
            }
        }));
        return this;
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        return handlingComponent.handle(event, context);
    }

    @Override
    public StatefulEventHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                    @Nonnull EventHandler eventHandler) {
        handlingComponent.subscribe(name, eventHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return handlingComponent.supportedEvents();
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("handlingComponent", handlingComponent);
        descriptor.describeProperty("stateManager", stateManager);
    }
}
