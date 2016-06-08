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

package org.axonframework.spring.config.xml;

import org.axonframework.commandhandling.AggregateAnnotationCommandHandler;
import org.axonframework.commandhandling.AnnotationCommandTargetResolver;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandTargetResolver;
import org.axonframework.messaging.MessageHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Field;

import static org.axonframework.common.ReflectionUtils.ensureAccessible;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:contexts/axon-namespace-support-context.xml"})
public class AggregateCommandHandlerBeanDefinitionParserTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("aggregateCommandHandlerWithoutTargetResolver")
    private AggregateAnnotationCommandHandler aggregateCommandHandlerWithoutTargetResolver;

    @Autowired
    @Qualifier("aggregateCommandHandlerWithTargetResolver")
    private AggregateAnnotationCommandHandler aggregateCommandHandlerWithTargetResolver;

    @Autowired
    @Qualifier("mockCommandBus1")
    private CommandBus mockCommandBus1;

    @Autowired
    @Qualifier("mockCommandBus2")
    private CommandBus mockCommandBus2;

    @Autowired
    private CommandTargetResolver commandTargetResolver;

    @Before
    public void setUp() throws Exception {
        assertNotNull("Failed to start application context", applicationContext);
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testBothCommandHandlersRegisterWithTheCommandBus() {
        verify(mockCommandBus1).subscribe(eq(SimpleAnnotatedAggregate.CreateSimpleAggregateCommand.class.getName()),
                                          any(MessageHandler.class));
        verify(mockCommandBus2).subscribe(eq(SimpleAnnotatedAggregate.CreateSimpleAggregateCommand.class.getName()),
                                          any(MessageHandler.class));
    }

    @Test
    public void testTargetResolverProperlyInjected() throws NoSuchFieldException, IllegalAccessException {
        Field commandTargetResolverField = AggregateAnnotationCommandHandler.class.getDeclaredField(
                "commandTargetResolver");
        ensureAccessible(commandTargetResolverField);
        Object targetResolver = commandTargetResolverField.get(aggregateCommandHandlerWithTargetResolver);
        assertSame(commandTargetResolver, targetResolver);
    }

    @Test
    public void testTargetResolverDefaultToAnnotationBased() throws NoSuchFieldException, IllegalAccessException {
        Field commandTargetResolverField = AggregateAnnotationCommandHandler.class.getDeclaredField(
                "commandTargetResolver");
        ensureAccessible(commandTargetResolverField);
        Object targetResolver = commandTargetResolverField.get(aggregateCommandHandlerWithoutTargetResolver);
        assertTrue(targetResolver instanceof AnnotationCommandTargetResolver);
    }
}
