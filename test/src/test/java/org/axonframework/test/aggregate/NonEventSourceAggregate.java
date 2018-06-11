/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.aggregate;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.model.AggregateIdentifier;

/**
 * @author Krzysztof Szymeczek
 */
class NonEventSourceAggregate {
    @AggregateIdentifier
    private String identifier;
    private String name;

    public NonEventSourceAggregate() {
    }

    public NonEventSourceAggregate(Object aggregateIdentifier, String name) {
        identifier = aggregateIdentifier.toString();
        this.name = name;
    }

    public void modifyName(NonEventSourceHandler.ModifyNameNonEventSourceCommand command) {
        name = command.getName();
        apply(new NonEventSourceNameChanged(identifier, name));
    }

    @CommandHandler
    public void on(NonEventSourceHandler.ModifyNameNonEventSource2Command command) {
        name = command.getName();
        apply(new NonEventSourceNameChanged(identifier, name));
    }
}
