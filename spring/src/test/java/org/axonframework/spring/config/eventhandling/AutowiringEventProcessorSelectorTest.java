/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.regex.Pattern;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class AutowiringEventProcessorSelectorTest {

    private static final SimpleEventProcessor EVENT_PROCESSOR_1 = new SimpleEventProcessor("eventProcessor1");
    private static final SimpleEventProcessor EVENT_PROCESSOR_2 = new SimpleEventProcessor("eventProcessor2");
    private static final SimpleEventProcessor EVENT_PROCESSOR_3 = new SimpleEventProcessor("eventProcessor3");

    @Autowired
    private AutowiringEventProcessorSelector testSubject;

    @Autowired
    @Qualifier("firstSelector")
    private EventProcessorSelector firstSelector;

    @Autowired
    @Qualifier("secondSelector")
    private EventProcessorSelector secondSelector;

    @Autowired
    @Qualifier("thirdSelector")
    private EventProcessorSelector thirdSelector;

    @Before
    public void setUp() {
        reset(firstSelector, secondSelector, thirdSelector);
    }

    @Test
    public void testAutowiringEventProcessorSelector_LastCandidateSelected() {
        //This will also test for a recursive dependency of the autowired eventProcessor selector on itself
        assertNotNull(testSubject);
        EventProcessor eventProcessor = testSubject.selectEventProcessor(event -> {
        });
        verify(firstSelector).selectEventProcessor(isA(EventListener.class));
        verify(secondSelector).selectEventProcessor(isA(EventListener.class));
        verify(thirdSelector).selectEventProcessor(isA(EventListener.class));
        assertSame(EVENT_PROCESSOR_3, eventProcessor);
    }

    @Test
    public void testAutowiringEventProcessorSelector_FirstCandidateSelected() {
        assertNotNull(testSubject);

        EventProcessor eventProcessor = testSubject.selectEventProcessor(new MyTestListener());
        assertSame(EVENT_PROCESSOR_1, eventProcessor);
        verify(firstSelector).selectEventProcessor(isA(EventListener.class));
        verify(secondSelector, never()).selectEventProcessor(isA(EventListener.class));
        verify(thirdSelector, never()).selectEventProcessor(isA(EventListener.class));
    }

    @Configuration
    public static class TestContext {

        @Bean
        public AutowiringEventProcessorSelector testSubject() {
            return new AutowiringEventProcessorSelector();
        }

        @Bean
        public EventProcessorSelector firstSelector() {
            return spy(new OrderedSelector(Integer.MIN_VALUE,
                                           new ClassNamePatternEventProcessorSelector(Pattern.compile(".*TestListener"),
                                                   EVENT_PROCESSOR_1)));
        }

        @Bean
        public EventProcessorSelector secondSelector() {
            return spy(new ClassNamePrefixEventProcessorSelector("java", EVENT_PROCESSOR_2));
        }

        @Bean
        public EventProcessorSelector thirdSelector() {
            return spy(new OrderedSelector(Integer.MAX_VALUE, new ClassNamePrefixEventProcessorSelector("org", EVENT_PROCESSOR_3)));
        }
    }

    private static class OrderedSelector implements Ordered, EventProcessorSelector {

        private final int order;
        private final EventProcessorSelector delegate;

        private OrderedSelector(int order, EventProcessorSelector delegate) {
            this.order = order;
            this.delegate = delegate;
        }

        @Override
        public EventProcessor selectEventProcessor(EventListener eventListener) {
            return delegate.selectEventProcessor(eventListener);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    private static class MyTestListener implements EventListener {

        @Override
        public void handle(EventMessage event) {
        }
    }
}
