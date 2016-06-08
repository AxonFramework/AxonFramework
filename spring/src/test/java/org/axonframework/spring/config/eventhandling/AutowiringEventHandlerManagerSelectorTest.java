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

import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
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
public class AutowiringEventHandlerManagerSelectorTest {

    private static final EventHandlerInvoker manager1 = new SimpleEventHandlerInvoker("manager1");
    private static final EventHandlerInvoker manager2 = new SimpleEventHandlerInvoker("manager2");
    private static final EventHandlerInvoker manager3 = new SimpleEventHandlerInvoker("manager3");

    @Autowired
    private AutowiringEventHandlerManagerSelector testSubject;

    @Autowired
    @Qualifier("firstSelector")
    private EventHandlerManagerSelector firstSelector;

    @Autowired
    @Qualifier("secondSelector")
    private EventHandlerManagerSelector secondSelector;

    @Autowired
    @Qualifier("thirdSelector")
    private EventHandlerManagerSelector thirdSelector;

    @Before
    public void setUp() {
        reset(firstSelector, secondSelector, thirdSelector);
    }

    @Test
    public void testAutowiringEventProcessorSelector_LastCandidateSelected() {
        //This will also test for a recursive dependency of the autowired eventProcessor selector on itself
        assertNotNull(testSubject);
        EventHandlerInvoker eventProcessor = testSubject.selectHandlerManager(event -> {
        });
        verify(firstSelector).selectHandlerManager(isA(EventListener.class));
        verify(secondSelector).selectHandlerManager(isA(EventListener.class));
        verify(thirdSelector).selectHandlerManager(isA(EventListener.class));
        assertSame(manager3, eventProcessor);
    }

    @Test
    public void testAutowiringEventProcessorSelector_FirstCandidateSelected() {
        assertNotNull(testSubject);

        EventHandlerInvoker eventProcessor = testSubject.selectHandlerManager(new MyTestListener());
        assertSame(manager1, eventProcessor);
        verify(firstSelector).selectHandlerManager(isA(EventListener.class));
        verify(secondSelector, never()).selectHandlerManager(isA(EventListener.class));
        verify(thirdSelector, never()).selectHandlerManager(isA(EventListener.class));
    }

    @Configuration
    public static class TestContext {

        @Bean
        public AutowiringEventHandlerManagerSelector testSubject() {
            return new AutowiringEventHandlerManagerSelector();
        }

        @Bean
        public EventHandlerManagerSelector firstSelector() {
            return spy(new OrderedSelector(Integer.MIN_VALUE,
                                           new ClassNamePatternEventHandlerManagerSelector(Pattern.compile(".*TestListener"),
                                                                                           manager1)));
        }

        @Bean
        public EventHandlerManagerSelector secondSelector() {
            return spy(new ClassNamePrefixEventHandlerManagerSelector("java", manager2));
        }

        @Bean
        public EventHandlerManagerSelector thirdSelector() {
            return spy(new OrderedSelector(Integer.MAX_VALUE, new ClassNamePrefixEventHandlerManagerSelector("org", manager3)));
        }
    }

    private static class OrderedSelector implements Ordered, EventHandlerManagerSelector {

        private final int order;
        private final EventHandlerManagerSelector delegate;

        private OrderedSelector(int order, EventHandlerManagerSelector delegate) {
            this.order = order;
            this.delegate = delegate;
        }

        @Override
        public EventHandlerInvoker selectHandlerManager(EventListener eventListener) {
            return delegate.selectHandlerManager(eventListener);
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
