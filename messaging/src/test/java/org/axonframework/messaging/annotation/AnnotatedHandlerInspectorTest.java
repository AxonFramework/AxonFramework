/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.interceptors.MessageHandlerInterceptor;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.collections.Sets;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests validating the {@link AnnotatedHandlerInspector}.
 *
 * @author Milan Savic
 */
class AnnotatedHandlerInspectorTest {

    private static ParameterResolverFactory parameterResolverFactory;
    private AnnotatedHandlerInspector<A> inspector;

    @BeforeAll
    static void init() {
        parameterResolverFactory = ClasspathParameterResolverFactory.forClass(AnnotatedHandlerInspectorTest.class);
    }

    @BeforeEach
    void setUp() {
        inspector = AnnotatedHandlerInspector.inspectType(A.class,
                                                          parameterResolverFactory,
                                                          ClasspathHandlerDefinition.forClass(A.class),
                                                          new HashSet<>(asList(D.class, C.class)));
    }

    // TODO This local static function should be replaced with a dedicated interface that converts types.
    // TODO However, that's out of the scope of the unit-of-rework branch and thus will be picked up later.
    private static MessageStream<? extends Message<?>> returnTypeConverter(Object result) {
        if (result instanceof CompletableFuture<?>) {
            return MessageStream.fromFuture(((CompletableFuture<?>) result).thenApply(GenericMessage::asMessage));
        }
        return MessageStream.just(GenericMessage.asMessage(result));
    }

    @Test
    void complexHandlerHierarchy() throws NoSuchMethodException {
        MethodInvokingMessageHandlingMember<pA> paHandle = new MethodInvokingMessageHandlingMember<>(
                pA.class.getMethod("paHandle", String.class), CommandMessage.class, String.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<A> aHandle = new MethodInvokingMessageHandlingMember<>(
                A.class.getMethod("aHandle", String.class), CommandMessage.class, String.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<A> aOn = new MethodInvokingMessageHandlingMember<>(
                A.class.getMethod("aOn", Integer.class), EventMessage.class, Integer.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<B> bHandle = new MethodInvokingMessageHandlingMember<>(
                B.class.getMethod("bHandle", Boolean.class), CommandMessage.class, Boolean.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<B> bOn = new MethodInvokingMessageHandlingMember<>(
                B.class.getMethod("bOn", Long.class), EventMessage.class, Long.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<C> cHandle = new MethodInvokingMessageHandlingMember<>(
                C.class.getMethod("cHandle", Boolean.class), CommandMessage.class, Boolean.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<C> cOn = new MethodInvokingMessageHandlingMember<>(
                C.class.getMethod("cOn", Integer.class), EventMessage.class, Integer.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );
        MethodInvokingMessageHandlingMember<D> dHandle = new MethodInvokingMessageHandlingMember<>(
                D.class.getMethod("dHandle", String.class), CommandMessage.class, String.class,
                parameterResolverFactory, AnnotatedHandlerInspectorTest::returnTypeConverter
        );

        Map<Class<?>, SortedSet<MessageHandlingMember<? super A>>> allHandlers = inspector.getAllHandlers();
        assertEquals(5, allHandlers.size());

        //noinspection OptionalGetWithoutIsPresent
        assertEquals(
                paHandle,
                allHandlers.get(pA.class)
                           .first()
                           .unwrap(MethodInvokingMessageHandlingMember.class).get()
        );
        //noinspection OptionalGetWithoutIsPresent
        assertEquals(
                paHandle,
                inspector.getHandlers(pA.class)
                         .findFirst()
                         .flatMap(h -> h.unwrap(MethodInvokingMessageHandlingMember.class)).get()
        );

        assertEquals(asList(aOn, aHandle, paHandle), unwrapToList(allHandlers.get(A.class).stream()));
        assertEquals(asList(aOn, aHandle, paHandle), unwrapToList(inspector.getHandlers(A.class)));

        assertEquals(asList(aOn, bOn, aHandle, bHandle, paHandle), unwrapToList(allHandlers.get(B.class).stream()));
        assertEquals(asList(aOn, bOn, aHandle, bHandle, paHandle), unwrapToList(inspector.getHandlers(B.class)));

        assertEquals(asList(aOn, cOn, aHandle, cHandle, paHandle), unwrapToList(allHandlers.get(C.class).stream()));
        assertEquals(asList(aOn, cOn, aHandle, cHandle, paHandle), unwrapToList(inspector.getHandlers(C.class)));

        assertEquals(
                asList(aOn, bOn, aHandle, bHandle, dHandle, paHandle),
                unwrapToList(allHandlers.get(D.class).stream())
        );
        assertEquals(
                asList(aOn, bOn, aHandle, bHandle, dHandle, paHandle),
                unwrapToList(inspector.getHandlers(D.class))
        );
    }

    @Test
    void doesNotRegisterAbstractHandlersTwice() {
        AnnotatedHandlerInspector<AB> aaInspector = AnnotatedHandlerInspector.inspectType(AB.class,
                                                                                          parameterResolverFactory);

        assertEquals(1, aaInspector.getAllHandlers().size());
        assertEquals(1, (int) aaInspector.getAllHandlers().values().stream().flatMap(Collection::stream).count());
    }

    private <T extends MessageHandlingMember<?>> List<MethodInvokingMessageHandlingMember<?>> unwrapToList(
            Stream<T> stream
    ) {
        //noinspection OptionalGetWithoutIsPresent
        return stream.map(e -> e.unwrap(MethodInvokingMessageHandlingMember.class)
                                .map(handler -> (MethodInvokingMessageHandlingMember<?>) handler).get())
                     .collect(Collectors.toList());
    }

    @Test
    void interceptors() throws Exception {
        D testTarget = new D();
        EventMessage<Object> testEvent = asEventMessage("Hello");
        EventMessage<Object> testEventTwo = asEventMessage(1);

        Map<Class<?>, SortedSet<MessageHandlingMember<? super A>>> interceptors = inspector.getAllInterceptors();
        assertEquals(5, interceptors.size());
        assertEquals(1, interceptors.get(pA.class).size());
        assertEquals(1, interceptors.get(A.class).size());
        assertEquals(2, interceptors.get(B.class).size());
        assertEquals(1, interceptors.get(C.class).size());
        assertEquals(2, interceptors.get(D.class).size());

        MessageHandlerInterceptorMemberChain<A> chain = inspector.chainedInterceptor(B.class);

        Optional<MessageHandlingMember<? super A>> optionalHandler = inspector.getHandlers(pA.class).findFirst();
        assertTrue(optionalHandler.isPresent());
        MessageHandlingMember<? super A> resultHandler = optionalHandler.get();
        chain.handleSync(testEvent, testTarget, resultHandler);
        assertThrows(MockException.class, () -> chain.handleSync(testEventTwo, testTarget, resultHandler));
    }

    @Test
    void getAllInspectedTypes() {
        Set<Class<?>> expectedInspectedTypes = Sets.newSet(pA.class, A.class, B.class, C.class, D.class);

        Set<Class<?>> resultInspectedTypes = inspector.getAllInspectedTypes();

        resultInspectedTypes.forEach(resultType -> assertTrue(expectedInspectedTypes.contains(resultType)));
        expectedInspectedTypes.forEach(expectedType -> assertTrue(resultInspectedTypes.contains(expectedType)));
    }

    @SuppressWarnings("unused")
    private static class pA {

        @CommandHandler
        public void paHandle(String a) {
        }

        @MessageHandlerInterceptor
        public void intercept(String e) {

        }
    }

    @SuppressWarnings("unused")
    private static class A extends pA {

        @CommandHandler
        public void aHandle(String a) {
        }

        @EventHandler
        public void aOn(Integer a) {
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    private static class C extends A {

        @CommandHandler
        public void cHandle(Boolean a) {
        }

        @EventHandler
        public void cOn(Integer a) {
        }
    }

    @SuppressWarnings("unused")
    private static class D extends B {

        @CommandHandler
        public void dHandle(String d) {
        }
    }

    public static abstract class AA {

        @CommandHandler
        public abstract String handleAbstractly(String command);
    }

    public static class AB extends AA {

        @Override
        public String handleAbstractly(String command) {
            return "Some result";
        }
    }
}
