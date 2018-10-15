/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.junit.*;

public class QueryEventHandlingTest {

    @Test
    public void testQueryHandlerAndEventHandlerCleanlyShutdown() {
        UserSummaryProjection userSummaryProjection = new UserSummaryProjection();

        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.configureCommandBus(c -> SimpleCommandBus.builder().build())
                  .configureQueryBus(c -> SimpleQueryBus.builder().build())
                  .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .registerQueryHandler(c -> userSummaryProjection);

        configurer.eventProcessing()
                  .registerEventHandler(c -> userSummaryProjection);

        Configuration configuration = configurer.buildConfiguration();

        configuration.start();
        configuration.shutdown();
    }

    class UserCreatedEvent {

        private final String userId;

        UserCreatedEvent(String userId) {
            this.userId = userId;
        }
    }

    class FindUserQuery {

        private final String userId;

        FindUserQuery(String userId) {
            this.userId = userId;
        }
    }

    class UserSummaryProjection {

        @EventHandler
        public void on(UserCreatedEvent event) {
            System.out.println("User created event handled");
        }

        @QueryHandler
        public UserSummaryProjection handle(FindUserQuery query) {
            System.out.println("User created query handled");
            return null;
        }
    }
}
