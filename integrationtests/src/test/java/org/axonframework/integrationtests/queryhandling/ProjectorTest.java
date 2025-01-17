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

package org.axonframework.integrationtests.queryhandling;

import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.junit.jupiter.api.*;

class ProjectorTest {

    // This ensures we do not wire Axon Server components
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;

    @Test
    void queryHandlerAndEventHandlerCleanlyShutdown() {
        UserSummaryProjection userSummaryProjection = new UserSummaryProjection();

        Configurer configurer = DefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES);
        configurer.configureCommandBus(c -> new SimpleCommandBus())
                  .configureQueryBus(c -> SimpleQueryBus.builder().build())
                  .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .registerQueryHandler(c -> userSummaryProjection);

        configurer.eventProcessing()
                  .registerEventHandler(c -> userSummaryProjection);

        Configuration configuration = configurer.buildConfiguration();

        configuration.start();
        configuration.shutdown();
    }

    @SuppressWarnings("unused")
    private static class UserCreatedEvent {

        private final String userId;

        UserCreatedEvent(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    @SuppressWarnings("unused")
    private static class FindUserQuery {

        private final String userId;

        FindUserQuery(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    @SuppressWarnings("unused")
    private static class UserSummaryProjection {

        @EventHandler
        public void on(UserCreatedEvent event) {
        }

        @QueryHandler
        public UserSummaryProjection handle(FindUserQuery query) {
            return null;
        }
    }
}
