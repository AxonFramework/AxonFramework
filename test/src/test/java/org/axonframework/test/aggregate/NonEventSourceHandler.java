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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Krzysztof Szymeczek
 */
public class NonEventSourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(NonEventSourceHandler.class);
    private Repository<NonEventSourceAggregate> repository;

    public NonEventSourceHandler(Repository<NonEventSourceAggregate> repository) {
        this.repository = repository;
    }

    @CommandHandler
    public void on(CreateNonEventSourceCommand command) {
        NonEventSourceAggregate nonEventSourceAggregate = new NonEventSourceAggregate(command.getId(), command.getName());
        try {
            repository.newInstance(() -> nonEventSourceAggregate);
        } catch (Exception e) {
            logger.error("Aggregate not saved", e);
        }
    }

    @CommandHandler
    public void on(ModifyNameNonEventSourceCommand command) {
        Aggregate<NonEventSourceAggregate> load = repository.load(command.id);
        load.execute((l) -> l.modifyName(command));
    }


    public static class CreateNonEventSourceCommand {
        private String id;

        public CreateNonEventSourceCommand(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private String name;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class ModifyNameNonEventSourceCommand {
        @TargetAggregateIdentifier
        private String id;

        public String getName() {
            return name;
        }

        private String name;

        public ModifyNameNonEventSourceCommand(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class ModifyNameNonEventSource2Command {
        @TargetAggregateIdentifier
        private String id;

        public String getName() {
            return name;
        }

        private String name;

        public ModifyNameNonEventSource2Command(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
