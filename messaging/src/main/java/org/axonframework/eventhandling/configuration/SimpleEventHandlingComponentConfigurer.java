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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.SequenceOverridingEventHandlingComponent;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.QualifiedName;

import java.util.Set;
import java.util.function.UnaryOperator;

class SimpleEventHandlingComponentConfigurer implements EventHandlingComponentConfigurer.SequencingPolicyPhase,
        EventHandlingComponentConfigurer.RequiredEventHandlerPhase,
        EventHandlingComponentConfigurer.AdditionalEventHandlerPhase,
        EventHandlingComponentConfigurer.Complete {

    private EventHandlingComponent component = new SimpleEventHandlingComponent();

    public SimpleEventHandlingComponentConfigurer() {
    }

    @Override
    public EventHandlingComponentConfigurer.RequiredEventHandlerPhase sequencingPolicy(
            @Nonnull SequencingPolicy sequencingPolicy) {
        this.component = new SequenceOverridingEventHandlingComponent(sequencingPolicy, component);
        return this;
    }

    @Override
    public EventHandlingComponentConfigurer.AdditionalEventHandlerPhase handles(@Nonnull QualifiedName name,
                                                                                @Nonnull EventHandler eventHandler) {
        component.subscribe(name, eventHandler);
        return this;
    }

    @Override
    public EventHandlingComponentConfigurer.AdditionalEventHandlerPhase handles(@Nonnull Set<QualifiedName> names,
                                                                                @Nonnull EventHandler eventHandler) {
        component.subscribe(names, eventHandler);
        return this;
    }

    @Override
    public EventHandlingComponentConfigurer.Complete decorated(
            @Nonnull UnaryOperator<EventHandlingComponent> decorator) {
        this.component = decorator.apply(component);
        return this;
    }

    @Override
    public EventHandlingComponent toComponent() {
        return component;
    }
}
