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

package org.axonframework.springboot.autoconfig.context;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.Random;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Dog extends Animal {

    // Field present to have the Cat aggregate differ in structure from the Dog for snapshot testing.
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private int ageInYears;

    @CommandHandler
    public Dog(CreateDogCommand command) {
        apply(new DogCreatedEvent(command.getAggregateId(), command.getName()));
    }

    @EventSourcingHandler
    public void on(DogCreatedEvent event) {
        this.aggregateId = event.getAggregateId();
        this.name = event.getName();
        this.ageInYears = new Random().nextInt(7);
    }

    public Dog() {
        // Required by Axon
    }
}
