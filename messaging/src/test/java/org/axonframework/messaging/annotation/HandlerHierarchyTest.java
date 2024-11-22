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

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.messaging.QualifiedName.className;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests sorting of handlers using {@link HandlerComparator}. Event types have complex hierarchical inheritance.
 *
 * @author Milan Savic
 */
class HandlerHierarchyTest {

    private interface C {

    }

    private interface D extends C {

    }

    private interface H extends D {

    }

    private interface E extends D {

    }

    private static abstract class F implements D {

    }

    private static class I implements H {

    }

    private static abstract class G implements E {

    }

    private static class A {

    }

    private static class B {

    }

    private static class MyEventHandler {

        @EventHandler
        public void handle(E event) {
        }

        @EventHandler
        public void handle(G event) {
        }

        @EventHandler
        public void handle(A event) {
        }

        @EventHandler
        public void handle(B event) {
        }

        @EventHandler
        public void handle(I event) {
        }

        @EventHandler
        public void handle(F event) {
        }
    }

    // TODO This local static function should be replaced with a dedicated interface that converts types.
    // TODO However, that's out of the scope of the unit-of-rework branch and thus will be picked up later.
    private static MessageStream<?> returnTypeConverter(Object result) {
        if (result instanceof CompletableFuture<?> future) {
            return MessageStream.fromFuture(future.thenApply(r -> new GenericMessage<>(className(r.getClass()), r)));
        }
        return MessageStream.just(new GenericMessage<>(className(result.getClass()), result));
    }

    @Test
    void hierarchySort() throws NoSuchMethodException {
        MultiParameterResolverFactory multiParameterResolverFactory = MultiParameterResolverFactory.ordered(new DefaultParameterResolverFactory());


        Class<? extends Message> eventMessageClass = EventMessage.class;
        MessageHandlingMember<?> bHandler = new MethodInvokingMessageHandlingMember<>(
                MyEventHandler.class.getMethod("handle", B.class),
                eventMessageClass,
                B.class,
                multiParameterResolverFactory,
                HandlerHierarchyTest::returnTypeConverter
        );
        MessageHandlingMember<?> iHandler = new MethodInvokingMessageHandlingMember<>(
                MyEventHandler.class.getMethod("handle", I.class),
                eventMessageClass,
                I.class,
                multiParameterResolverFactory,
                HandlerHierarchyTest::returnTypeConverter
        );
        MessageHandlingMember<?> fHandler = new MethodInvokingMessageHandlingMember<>(
                MyEventHandler.class.getMethod("handle", F.class),
                eventMessageClass,
                F.class,
                multiParameterResolverFactory,
                HandlerHierarchyTest::returnTypeConverter
        );
        MessageHandlingMember<?> aHandler = new MethodInvokingMessageHandlingMember<>(
                MyEventHandler.class.getMethod("handle", A.class),
                eventMessageClass,
                A.class,
                multiParameterResolverFactory,
                HandlerHierarchyTest::returnTypeConverter
        );
        MessageHandlingMember<?> gHandler = new MethodInvokingMessageHandlingMember<>(
                MyEventHandler.class.getMethod("handle", G.class),
                eventMessageClass,
                G.class,
                multiParameterResolverFactory,
                HandlerHierarchyTest::returnTypeConverter
        );
        MessageHandlingMember<?> eHandler = new MethodInvokingMessageHandlingMember<>(
                MyEventHandler.class.getMethod("handle", E.class),
                eventMessageClass,
                E.class,
                multiParameterResolverFactory,
                HandlerHierarchyTest::returnTypeConverter
        );

        List<MessageHandlingMember<?>> handlers = Arrays.asList(bHandler,
                                                                iHandler,
                                                                fHandler,
                                                                aHandler,
                                                                gHandler,
                                                                eHandler);

        handlers.sort(HandlerComparator.instance());
        assertTrue(handlers.indexOf(gHandler) < handlers.indexOf(eHandler));
    }
}
