/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/simple-disruptor-context.xml"})
public class DisruptorContextConfigurationTest {

    @Autowired
    private CommandBus commandBus;

    // Tests a scenario where the order in which command bus and event bus are declared could cause a circular dependency error in Spring
    @Test
    public void testCommandBus() throws Exception {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new StubCommand("test")));
    }

    public static class MyAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String id;

        public MyAggregate() {
        }

        @CommandHandler
        public MyAggregate(StubCommand command) {
            apply(new SimpleEvent(command.id));
        }

        @EventSourcingHandler
        public void on(SimpleEvent event) {
            this.id = event.getAggregateIdentifier();
        }
    }

    public static class StubCommand {

        @TargetAggregateIdentifier
        private final String id;

        public StubCommand(String id) {
            this.id = id;
        }
    }
}
