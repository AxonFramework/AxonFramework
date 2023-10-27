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

package org.axonframework.integrationtests.cache;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.eventhandling.EventHandler;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * @author Allard Buijze
 */
public class TestAggregateRoot {

    @AggregateIdentifier
    private String id;

    TestAggregateRoot() {
    }

    @CommandHandler
    public TestAggregateRoot(CreateCommand cmd) {
        apply(new CreatedEvent(cmd.id));
    }

    @CommandHandler
    public TestAggregateRoot(FailingCreateCommand cmd) {
        throw new IllegalArgumentException("I don't like this");
    }

    @CommandHandler
    public void throwException(FailCommand cmd) {
        throw new IllegalArgumentException("I don't like this");
    }

    @EventHandler
    public void onMessage(CreatedEvent event) {
        this.id = event.id;
    }

    public static class CreateCommand {

        @TargetAggregateIdentifier
        private final String id;

        public CreateCommand(String id) {
            this.id = id;
        }
    }

    public static class FailingCreateCommand {

        @TargetAggregateIdentifier
        private final String id;

        public FailingCreateCommand(String id) {
            this.id = id;
        }
    }

    public static class FailCommand {

        @TargetAggregateIdentifier
        private final String id;

        public FailCommand(String id) {
            this.id = id;
        }
    }

    public static class CreatedEvent {

        private final String id;

        public CreatedEvent(String id) {
            this.id = id;
        }
    }
}
