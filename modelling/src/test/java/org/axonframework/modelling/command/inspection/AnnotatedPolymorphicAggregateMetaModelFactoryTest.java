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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.junit.jupiter.api.*;

import java.lang.reflect.Executable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.persistence.Id;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for polymorphic aggregate model.
 *
 * @author Milan Savic
 */
class AnnotatedPolymorphicAggregateMetaModelFactoryTest {

    @Test
    void testPolymorphicHierarchy() throws NoSuchMethodException {
        Executable aHandle = A.class.getMethod("handle", String.class);
        Executable bHandle = B.class.getMethod("handle", Boolean.class);
        Executable cHandle = C.class.getMethod("handle", Boolean.class);
        Executable aOn = A.class.getMethod("on", Integer.class);
        Executable bOn = B.class.getMethod("on", Long.class);
        Executable cOn = C.class.getMethod("on", Integer.class);
        Executable aIntercept = A.class.getMethod("intercept", String.class);
        Executable bIntercept = B.class.getMethod("intercept", String.class);
        Executable cIntercept = C.class.getMethod("intercept", String.class);

        AnnotatedAggregateMetaModelFactory factory = new AnnotatedAggregateMetaModelFactory();
        AggregateModel<A> model = factory.createModel(A.class, new HashSet<>(asList(B.class, C.class)));
        Map<Class<?>, List<MessageHandlingMember<? super A>>> allCommandHandlers = model.allCommandHandlers();
        Map<Class<?>, List<MessageHandlingMember<? super A>>> allEventHandlers = model.allEventHandlers();
        Map<Class<?>, List<MessageHandlingMember<? super A>>> allCommandHandlerInterceptors = model
                .allCommandHandlerInterceptors();

        assertEquals(3, allCommandHandlers.size());
        assertEquals(3, allEventHandlers.size());
        assertEquals(3, allCommandHandlerInterceptors.size());

        assertEquals(singletonList(aHandle), executables(allCommandHandlers.get(A.class)));
        assertEquals(singletonList(aHandle), executables(model.commandHandlers(A.class)));
        assertEquals(singletonList(aIntercept), executables(allCommandHandlerInterceptors.get(A.class)));
        assertEquals(singletonList(aIntercept), executables(model.commandHandlerInterceptors(A.class)));
        assertEquals(singletonList(aOn), executables(allEventHandlers.get(A.class)));

        assertEquals(asList(aHandle, bHandle), executables(allCommandHandlers.get(B.class)));
        assertEquals(asList(aHandle, bHandle), executables(model.commandHandlers(B.class)));
        assertEquals(asList(aIntercept, bIntercept), executables(allCommandHandlerInterceptors.get(B.class)));
        assertEquals(asList(aIntercept, bIntercept), executables(model.commandHandlerInterceptors(B.class)));
        assertEquals(asList(aOn, bOn), executables(allEventHandlers.get(B.class)));

        assertEquals(asList(aHandle, cHandle), executables(allCommandHandlers.get(C.class)));
        assertEquals(asList(aHandle, cHandle), executables(model.commandHandlers(C.class)));
        assertEquals(asList(aIntercept, cIntercept), executables(allCommandHandlerInterceptors.get(C.class)));
        assertEquals(asList(aIntercept, cIntercept), executables(model.commandHandlerInterceptors(C.class)));
        assertEquals(asList(aOn, cOn), executables(allEventHandlers.get(C.class)));
    }

    @Test
    void testUniqueAggregateIdentifierField() {
        assertThrows(AggregateModellingException.class,
                     () -> AnnotatedAggregateMetaModelFactory
                             .inspectAggregate(D.class, new HashSet<>(singleton(E.class))));
    }

    @Test
    void testUniqueAggregateIdentifierFieldShouldIgnorePersistenceId() {
        // should not throw an exception
        AnnotatedAggregateMetaModelFactory.inspectAggregate(D.class, new HashSet<>(singleton(F.class)));
    }

    private <T> List<Executable> executables(List<MessageHandlingMember<? super T>> members) {
        return executables(members.stream());
    }

    private <T> List<Executable> executables(Stream<MessageHandlingMember<? super T>> members) {
        return members.map(mhm -> mhm.unwrap(Executable.class).get())
                      .collect(toList());
    }

    private static class A {

        @CommandHandlerInterceptor
        public void intercept(String a) {
        }

        @CommandHandler
        public void handle(String a) {
        }

        @EventHandler
        public void on(Integer a) {
        }
    }

    private static class B extends A {

        @CommandHandlerInterceptor
        public void intercept(String a) {
        }

        @CommandHandler
        public void handle(Boolean a) {
        }

        @EventHandler
        public void on(Long b) {
        }
    }

    private static class C extends A {

        @CommandHandlerInterceptor
        public void intercept(String a) {
        }

        @CommandHandler
        public void handle(Boolean a) {
        }

        @EventHandler
        public void on(Integer a) {
        }
    }

    private static class D {

        @AggregateIdentifier
        private String id;

        @CommandHandler
        public void handle(String a) {
        }
    }

    private static class E extends D {

        @AggregateIdentifier
        private String id1;

        @CommandHandler
        public void handle(Integer a) {
        }
    }

    private static class F extends D {

        @Id
        private String id1;

        @CommandHandler
        public void handle(Integer a) {
        }
    }
}
