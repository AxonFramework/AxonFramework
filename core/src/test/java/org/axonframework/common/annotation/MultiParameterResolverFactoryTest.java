/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.common.annotation;

import org.axonframework.common.Priority;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.Message;
import org.junit.*;
import org.mockito.*;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class MultiParameterResolverFactoryTest {

    private ParameterResolverFactory mockFactory1;
    private ParameterResolverFactory mockFactory2;

    private ParameterResolver mockResolver1;
    private ParameterResolver mockResolver2;

    private MultiParameterResolverFactory testSubject;

    @Before
    public void setUp() throws Exception {
        mockFactory1 = mock(ParameterResolverFactory.class);
        mockFactory2 = mock(ParameterResolverFactory.class);

        mockResolver1 = mock(ParameterResolver.class);
        mockResolver2 = mock(ParameterResolver.class);

        when(mockFactory1.createInstance(Matchers.<Annotation[]>anyObject(),
                                         any(Class.class),
                                         Matchers.<Annotation[]>any())).thenReturn(mockResolver1);
        when(mockFactory2.createInstance(Matchers.<Annotation[]>anyObject(),
                                         any(Class.class),
                                         Matchers.<Annotation[]>any())).thenReturn(mockResolver2);

        testSubject = new MultiParameterResolverFactory(mockFactory1, mockFactory2);
    }

    @Test
    public void testResolversQueriedInOrderProvided() throws Exception {
        ParameterResolver factory = testSubject.createInstance(new Annotation[0], String.class, new Annotation[0]);
        assertFalse(factory.matches(null));

        InOrder inOrder = inOrder(mockFactory1, mockFactory2, mockResolver1, mockResolver2);
        inOrder.verify(mockFactory1).createInstance(Matchers.<Annotation[]>anyObject(),
                                                    eq(String.class),
                                                    Matchers.<Annotation[]>any());
        inOrder.verify(mockResolver1).matches(any(Message.class));

        verify(mockFactory2, never()).createInstance(Matchers.<Annotation[]>anyObject(),
                                                     eq(String.class),
                                                     Matchers.<Annotation[]>any());

        verify(mockResolver2, never()).matches(any(Message.class));
    }

    @Test
    public void testFirstMatchingResolverMayReturnValue() throws Exception {
        final EventMessage<Object> message = GenericEventMessage.asEventMessage("test");
        when(mockFactory1.createInstance(Matchers.<Annotation[]>any(), any(Class.class), Matchers.<Annotation[]>any()))
                .thenReturn(null);
        when(mockResolver2.matches(message)).thenReturn(true);
        when(mockResolver2.resolveParameterValue(message)).thenReturn("Resolved");

        ParameterResolver factory = testSubject.createInstance(new Annotation[0], String.class, new Annotation[0]);
        assertTrue(factory.matches(message));
        assertEquals("Resolved", factory.resolveParameterValue(message));

        verify(mockResolver1, never()).resolveParameterValue(any(Message.class));
    }

    @Test
    public void testNestedParameterResolversAreOrdered() {
        final LowPrioParameterResolverFactory lowPrio = new LowPrioParameterResolverFactory();
        final HighPrioParameterResolverFactory highPrio = new HighPrioParameterResolverFactory();
        testSubject = MultiParameterResolverFactory.ordered(mockFactory1,
                                                        new MultiParameterResolverFactory(lowPrio, mockFactory2),
                                                        new MultiParameterResolverFactory(highPrio));

        assertEquals(Arrays.asList(highPrio, mockFactory1, mockFactory2, lowPrio), testSubject.getDelegates());
    }

    @Priority(Priority.LOW)
    private static class LowPrioParameterResolverFactory extends AbstractNoopParameterResolverFactory {

    }

    @Priority(Priority.HIGH)
    private static class HighPrioParameterResolverFactory extends AbstractNoopParameterResolverFactory {

    }

    private static class AbstractNoopParameterResolverFactory implements ParameterResolverFactory {

        @Override
        public ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                                Annotation[] parameterAnnotations) {
            return null;
        }
    }


}
