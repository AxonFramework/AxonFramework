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

package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Cat extends Animal {

    // Field present to have the Dog aggregate differ in structure from the Cat for snapshot testing.
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private String hairColor;

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    public void handle(CreateCatCommand command) {
        apply(new CatCreatedEvent(command.getAggregateId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(CatCreatedEvent event) {
        this.aggregateId = event.getAggregateId();
        this.name = event.getName();
        this.hairColor = UUID.randomUUID().toString();
    }

    public Cat() {
        // Required by Axon
    }
}
