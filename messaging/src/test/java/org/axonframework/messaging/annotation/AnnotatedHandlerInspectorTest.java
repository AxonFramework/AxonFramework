/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedSet;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnnotatedHandlerInspector}.
 *
 * @author Milan Savic
 */
public class AnnotatedHandlerInspectorTest {

    @Test
    public void testComplexHandlerHierarchy() throws NoSuchMethodException {
        DefaultParameterResolverFactory parameterResolverFactory = new DefaultParameterResolverFactory();
        AnnotatedHandlerInspector<A> inspector =
                AnnotatedHandlerInspector.inspectType(A.class,
                                                      parameterResolverFactory,
                                                      new AnnotatedMessageHandlingMemberDefinition(),
                                                      new HashSet<>(asList(D.class, C.class)));
        AnnotatedMessageHandlingMember<pA> paHandle = new AnnotatedMessageHandlingMember<>(pA.class.getMethod(
                "paHandle", String.class), CommandMessage.class, String.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<A> aHandle = new AnnotatedMessageHandlingMember<>(A.class.getMethod(
                "aHandle", String.class), CommandMessage.class, String.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<A> aOn = new AnnotatedMessageHandlingMember<>(A.class.getMethod(
                "aOn", Integer.class), EventMessage.class, Integer.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<B> bHandle = new AnnotatedMessageHandlingMember<>(B.class.getMethod(
                "bHandle", Boolean.class), CommandMessage.class, Boolean.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<B> bOn = new AnnotatedMessageHandlingMember<>(B.class.getMethod(
                "bOn", Long.class), EventMessage.class, Long.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<C> cHandle = new AnnotatedMessageHandlingMember<>(C.class.getMethod(
                "cHandle", Boolean.class), CommandMessage.class, Boolean.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<C> cOn = new AnnotatedMessageHandlingMember<>(C.class.getMethod(
                "cOn", Integer.class), EventMessage.class, Integer.class, parameterResolverFactory);
        AnnotatedMessageHandlingMember<D> dHandle = new AnnotatedMessageHandlingMember<>(D.class.getMethod(
                "dHandle", String.class), CommandMessage.class, String.class, parameterResolverFactory);

        Map<Class<?>, SortedSet<MessageHandlingMember<? super A>>> allHandlers = inspector.getAllHandlers();
        assertEquals(5, allHandlers.size());

        assertEquals(paHandle, allHandlers.get(pA.class).first());
        assertEquals(paHandle, inspector.getHandlers(pA.class).findFirst().get());

        assertEquals(asList(aOn, aHandle, paHandle), new ArrayList<>(allHandlers.get(A.class)));
        assertEquals(asList(aOn, aHandle, paHandle), inspector.getHandlers(A.class).collect(toList()));

        assertEquals(asList(aOn, bOn, aHandle, bHandle, paHandle), new ArrayList<>(allHandlers.get(B.class)));
        assertEquals(asList(aOn, bOn, aHandle, bHandle, paHandle), inspector.getHandlers(B.class).collect(toList()));

        assertEquals(asList(aOn, cOn, aHandle, cHandle, paHandle), new ArrayList<>(allHandlers.get(C.class)));
        assertEquals(asList(aOn, cOn, aHandle, cHandle, paHandle), inspector.getHandlers(C.class).collect(toList()));

        assertEquals(asList(aOn, bOn, aHandle, bHandle, dHandle, paHandle), new ArrayList<>(allHandlers.get(D.class)));
        assertEquals(asList(aOn, bOn, aHandle, bHandle, dHandle, paHandle),
                     inspector.getHandlers(D.class).collect(toList()));
    }

    private static class pA {

        @CommandHandler
        public void paHandle(String a) {
        }
    }

    private static class A extends pA {

        @CommandHandler
        public void aHandle(String a) {
        }

        @EventHandler
        public void aOn(Integer a) {
        }
    }

    private static class B extends A {

        @CommandHandler
        public void bHandle(Boolean a) {
        }

        @EventHandler
        public void bOn(Long b) {
        }
    }

    private static class C extends A {

        @CommandHandler
        public void cHandle(Boolean a) {
        }

        @EventHandler
        public void cOn(Integer a) {
        }
    }

    private static class D extends B {

        @CommandHandler
        public void dHandle(String d) {
        }
    }
}