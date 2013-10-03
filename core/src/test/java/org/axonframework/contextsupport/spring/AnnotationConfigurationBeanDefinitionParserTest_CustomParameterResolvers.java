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

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.EndSaga;
import org.axonframework.saga.annotation.SagaEventHandler;
import org.axonframework.saga.annotation.StartSaga;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.Executor;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-annotations-context.xml"})
public class AnnotationConfigurationBeanDefinitionParserTest_CustomParameterResolvers {

    @Autowired
    private Executor executor;

    @Autowired
    private EventBus eventBus;

    @Before
    public void startup() {
        reset(executor);
    }

    @Test
    @DirtiesContext
    public void testSpringBeanProperlyInjectedInAnnotatedAggregates() throws Exception {
        new StubAggregate().doSomething();
        verify(executor).execute(isA(Runnable.class));
    }

    @Test
    @DirtiesContext
    public void testSpringBeanProperlyInjectedInAnnotatedAggregates_Again() throws Exception {
        // this test ensures that shutting down and starting a spring context doesn't affect behavior
        new StubAggregate().doSomething();
        verify(executor).execute(isA(Runnable.class));
    }

    @Test
    @DirtiesContext
    public void testSpringBeanProperlyInjectedInAnnotatedSaga() throws Exception {
        eventBus.publish(GenericEventMessage.asEventMessage(new SimpleEvent("test")));

        // both the sync and async should invoke the executor
        verify(executor, timeout(1000).times(2)).execute(isA(Runnable.class));
    }

    @Test
    @DirtiesContext
    public void testSpringBeanProperlyInjectedInAnnotatedSaga_Again() throws Exception {
        eventBus.publish(GenericEventMessage.asEventMessage(new SimpleEvent("test")));

        // both the sync and async should invoke the executor
        verify(executor, timeout(1000).times(2)).execute(isA(Runnable.class));
    }


    public static class StubSaga extends AbstractAnnotatedSaga {

        @EndSaga
        @StartSaga
        @SagaEventHandler(associationProperty = "aggregateIdentifier")
        public void on(SimpleEvent event, Executor someResourceFromSpringContext) {
            someResourceFromSpringContext.execute(new Runnable() {
                @Override
                public void run() {
                }
            });
        }
    }

    public static class StubAggregate extends AbstractAnnotatedAggregateRoot {

        @AggregateIdentifier
        private String id;

        public void doSomething() {
            apply(new SimpleEvent("abc"));
        }

        @EventHandler
        public void on(SimpleEvent event, Executor someResourceFromSpringContext) {
            this.id = event.getAggregateIdentifier();
            someResourceFromSpringContext.execute(new Runnable() {
                @Override
                public void run() {
                }
            });
        }
    }
}
