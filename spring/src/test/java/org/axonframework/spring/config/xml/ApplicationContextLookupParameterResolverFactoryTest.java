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

package org.axonframework.spring.config.xml;

import org.axonframework.common.annotation.FixedValueParameterResolver;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/contexts/custom-parameter-resolver-context.xml")
public class ApplicationContextLookupParameterResolverFactoryTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private CustomParameterEventListener listener;

    @Test
    public void testPublishEvent() throws Exception {
        eventBus.publish(asEventMessage(new CustomEvent()));

        assertEquals(1, listener.invocationCount());
    }

    public static class CustomParameterEventListener {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @EventHandler
        public void handleEvent(CustomEvent event, TimeUnit timeUnit) {
            assertNotNull(event);
            assertNotNull(timeUnit);
            invocationCount.incrementAndGet();
        }

        public int invocationCount() {
            return invocationCount.get();
        }
    }

    public static class CustomEvent {

    }

    public static class CustomParameterResolverFactory implements ParameterResolverFactory {

        @Override
        public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
            if (TimeUnit.class.isAssignableFrom(parameters[parameterIndex].getType())) {
                return new FixedValueParameterResolver<>(TimeUnit.DAYS);
            }
            return null;
        }
    }
}
