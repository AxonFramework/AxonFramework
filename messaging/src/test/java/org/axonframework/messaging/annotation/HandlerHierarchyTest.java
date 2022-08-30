/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests sorting of handlers using {@link HandlerComparator}. Event types have complex hierarchical inheritance.
 *
 * @author Milan Savic
 */
class HandlerHierarchyTest {

    private interface C {}
    private interface D extends C {}
    private interface H extends D {}
    private interface E extends D {}
    private static abstract class F implements D {}
    private static class I implements H {}
    private static abstract class G implements E {}
    private static class A {}
    private static class B {}

    private static class MyEventHandler {

        @EventHandler public void handle(E event) {}
        @EventHandler public void handle(G event) {}
        @EventHandler public void handle(A event) {}
        @EventHandler public void handle(B event) {}
        @EventHandler public void handle(I event) {}
        @EventHandler public void handle(F event) {}
    }

    @Test
    void hierarchySort() throws NoSuchMethodException {
        MultiParameterResolverFactory multiParameterResolverFactory = MultiParameterResolverFactory.ordered(new DefaultParameterResolverFactory());

        MessageHandlingMember<?> bHandler = new AnnotatedMessageHandlingMember<>(MyEventHandler.class.getMethod("handle", B.class),
                                                                                 EventMessage.class,
                                                                                 B.class,
                                                                                 multiParameterResolverFactory);
        MessageHandlingMember<?> iHandler = new AnnotatedMessageHandlingMember<>(MyEventHandler.class.getMethod("handle", I.class),
                                                                                 EventMessage.class,
                                                                                 I.class,
                                                                                 multiParameterResolverFactory);
        MessageHandlingMember<?> fHandler = new AnnotatedMessageHandlingMember<>(MyEventHandler.class.getMethod("handle", F.class),
                                                                                 EventMessage.class,
                                                                                 F.class,
                                                                                 multiParameterResolverFactory);
        MessageHandlingMember<?> aHandler = new AnnotatedMessageHandlingMember<>(MyEventHandler.class.getMethod("handle", A.class),
                                                                                 EventMessage.class,
                                                                                 A.class,
                                                                                 multiParameterResolverFactory);
        MessageHandlingMember<?> gHandler = new AnnotatedMessageHandlingMember<>(MyEventHandler.class.getMethod("handle", G.class),
                                                                                 EventMessage.class,
                                                                                 G.class,
                                                                                 multiParameterResolverFactory);
        MessageHandlingMember<?> eHandler = new AnnotatedMessageHandlingMember<>(MyEventHandler.class.getMethod("handle", E.class),
                                                                                 EventMessage.class,
                                                                                 E.class,
                                                                                 multiParameterResolverFactory);

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
