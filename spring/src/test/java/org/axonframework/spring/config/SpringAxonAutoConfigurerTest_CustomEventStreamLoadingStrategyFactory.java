/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.spring.config;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertNotNull;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStreamLoadingStrategyFactory;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SpringAxonAutoConfigurerTest_CustomEventStreamLoadingStrategyFactory {

    @Autowired
    private org.axonframework.config.Configuration axonConfig;

    @Autowired
    private EventStreamLoadingStrategyFactory eventStreamLoadingStrategyFactory;

    @Test
    public void contextWiresMainComponents() {
        assertNotNull(axonConfig);
        assertNotNull(axonConfig.repository(Context.MyAggregate.class));
        verify(eventStreamLoadingStrategyFactory, times(1)).create(any());
    }

    @EnableAxon
    @Scope
    @Configuration
    public static class Context {

        @Bean
        public EventStreamLoadingStrategyFactory eventStreamLoadingStrategyFactory () {
            EventStreamLoadingStrategyFactory factory = mock(EventStreamLoadingStrategyFactory.class);
            when(factory.create(any())).thenReturn(EventSourcingRepository.DEFAULT_EVENT_STREAM_LOADING_STRATEGY);
            return factory;
        }

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Aggregate
        public static class MyAggregate {

            @AggregateIdentifier
            private String id;

            @CommandHandler
            public MyAggregate (Long command) {
                apply(command);
            }

            @EventSourcingHandler
            public void on(Long event) {
                this.id = Long.toString(event);
            }
        }
    }
}
