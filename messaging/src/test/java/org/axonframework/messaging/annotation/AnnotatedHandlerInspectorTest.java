/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AnnotatedHandlerInspector}.
 *
 * @author Milan Savic
 */
public class AnnotatedHandlerInspectorTest {

    private static ParameterResolverFactory parameterResolverFactory;
    private AnnotatedHandlerInspector<A> inspector;

    @BeforeAll
    public static void init() {
        parameterResolverFactory = ClasspathParameterResolverFactory.forClass(AnnotatedHandlerInspectorTest.class);
    }

    @BeforeEach
    public void setUp() {
        inspector = AnnotatedHandlerInspector.inspectType(A.class,
                                                          parameterResolverFactory,
                                                          ClasspathHandlerDefinition.forClass(A.class),
                                                          new HashSet<>(asList(D.class, C.class)));
    }

    @Test
    public void testComplexHandlerHierarchy() throws NoSuchMethodException {
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

        assertEquals(paHandle, allHandlers.get(pA.class).first().unwrap(AnnotatedMessageHandlingMember.class).get());
        assertEquals(paHandle, inspector.getHandlers(pA.class).findFirst().flatMap(h -> h.unwrap(AnnotatedMessageHandlingMember.class)).get());

        assertEquals(asList(aOn, aHandle, paHandle), unwrapToList(allHandlers.get(A.class).stream()));
        assertEquals(asList(aOn, aHandle, paHandle), unwrapToList(inspector.getHandlers(A.class)));

        assertEquals(asList(aOn, bOn, aHandle, bHandle, paHandle), unwrapToList(allHandlers.get(B.class).stream()));
        assertEquals(asList(aOn, bOn, aHandle, bHandle, paHandle), unwrapToList(inspector.getHandlers(B.class)));

        assertEquals(asList(aOn, cOn, aHandle, cHandle, paHandle), unwrapToList(allHandlers.get(C.class).stream()));
        assertEquals(asList(aOn, cOn, aHandle, cHandle, paHandle), unwrapToList(inspector.getHandlers(C.class)));

        assertEquals(asList(aOn, bOn, aHandle, bHandle, dHandle, paHandle), unwrapToList(allHandlers.get(D.class).stream()));
        assertEquals(asList(aOn, bOn, aHandle, bHandle, dHandle, paHandle), unwrapToList(inspector.getHandlers(D.class)));
    }

    private <T extends MessageHandlingMember<?>> List<AnnotatedMessageHandlingMember> unwrapToList(Stream<T> stream) {
        return stream.map(e -> e.unwrap(AnnotatedMessageHandlingMember.class).get()).collect(Collectors.toList());
    }

    @Test
    public void testInterceptors() throws Exception {
        Map<Class<?>, SortedSet<MessageHandlingMember<? super A>>> interceptors = inspector.getAllInterceptors();
        assertEquals(5, interceptors.size());
        assertEquals(1, interceptors.get(pA.class).size());
        assertEquals(1, interceptors.get(A.class).size());
        assertEquals(2, interceptors.get(B.class).size());
        assertEquals(1, interceptors.get(C.class).size());
        assertEquals(2, interceptors.get(D.class).size());

        MessageHandlerInterceptorMemberChain<A> chain = inspector.chainedInterceptor(B.class);

        chain.handle(asEventMessage("Hello"), new D(), inspector.getHandlers(pA.class).findFirst().get());
        assertThrows(MockException.class, () ->
                chain.handle(asEventMessage(1), new D(), inspector.getHandlers(pA.class).findFirst().get())
        );

    }

    private static class pA {

        @CommandHandler
        public void paHandle(String a) {
        }

        @MessageHandlerInterceptor
        public void intercept(String e) {

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

        @MessageHandlerInterceptor
        public void intercept(Integer e, InterceptorChain chain) {
            throw new MockException("Faking exception in interceptor");
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