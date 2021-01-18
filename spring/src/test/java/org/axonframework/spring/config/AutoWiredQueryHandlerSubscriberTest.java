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

package org.axonframework.spring.config;

import org.axonframework.config.EventProcessingModule;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.spring.config.event.QueryHandlersSubscribedEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.*;

/**
 * Test class validating the invocation order of handler registration and publication of the {@link
 * QueryHandlersSubscribedEvent} through the {@link QueryHandlerSubscriber}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AutoWiredQueryHandlerSubscriberTest {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private QueryBus queryBus;
    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired
    private QueryHandlersSubscribedEventListener handlersSubscribedEventListener;

    @Test
    void testEventIsPublishedAfterCommandBusSubscription() {
        InOrder subscriptionOrder = inOrder(queryBus, handlersSubscribedEventListener);
        subscriptionOrder.verify(queryBus).subscribe(eq("testQuery"), eq(String.class), any());
        subscriptionOrder.verify(queryBus).subscribe(eq("otherTestQuery"), eq(Integer.class), any());
        subscriptionOrder.verify(handlersSubscribedEventListener).on(any());
        subscriptionOrder.verifyNoMoreInteractions();
    }

    @Configuration
    @Import({SpringAxonAutoConfigurer.ImportSelector.class, AnnotationDrivenRegistrar.class})
    public static class Context {

        @Bean
        public QueryBus queryBus() {
            return mock(QueryBus.class);
        }

        @Bean
        public TestQueryHandler testQueryHandler() {
            return new TestQueryHandler();
        }

        @Bean
        public OtherTestQueryHandler otherTestQueryHandler() {
            return new OtherTestQueryHandler();
        }

        @Bean
        public QueryHandlersSubscribedEventListener handlersSubscribedEventListener() {
            return spy(new QueryHandlersSubscribedEventListener());
        }

        // Required by the SpringAxonAutoConfigurer to start
        @Bean
        public EventProcessingModule eventProcessingModule() {
            return mock(EventProcessingModule.class);
        }
    }

    private static class TestQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "testQuery")
        public String handle(String query) {
            return "Not Important";
        }
    }

    private static class OtherTestQueryHandler {

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "otherTestQuery")
        public Integer handle(Integer query) {
            return 1337;
        }
    }

    private static class QueryHandlersSubscribedEventListener {

        @SuppressWarnings("unused")
        @EventListener
        public void on(QueryHandlersSubscribedEvent event) {
            // Not important
        }
    }
}
