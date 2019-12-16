/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.queryhandling.annotation;

import org.axonframework.messaging.annotation.*;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the {@link MethodQueryMessageHandlerDefinition} correctly deals with return types, as well as for
 * example {@link java.util.concurrent.Future} and {@link Optional} which contain a generic type.
 */
class MethodQueryMessageHandlerDefinitionTest {

    private MethodQueryMessageHandlerDefinition testSubject;
    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() throws Exception {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new MethodQueryMessageHandlerDefinition();
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Test
    void testVoidNotAcceptedAsReturnType() {
        assertThrows(UnsupportedHandlerException.class, () -> messageHandler("illegalQueryResponseType"));
    }

    @Test
    void testFutureResponseTypeUnwrapped() {
        QueryHandlingMember handler = messageHandler("futureReturnType");
        assertEquals(String.class, handler.getResultType());
    }

    @Test
    void testOptionalResponseTypeUnwrapped() throws Exception {
        QueryHandlingMember handler = messageHandler("optionalReturnType");
        assertEquals(String.class, handler.getResultType());

        GenericQueryMessage<String, String> message = new GenericQueryMessage<>("mock", ResponseTypes.instanceOf(String.class));

        assertTrue(handler.canHandle(message));

        Object invocationResult = handler.handle(message, this);
        assertNull(invocationResult);
    }


    @Test
    void testUnspecifiedOptionalResponseTypeUnwrapped() {
        QueryHandlingMember handler = messageHandler("unspecifiedOptionalType");
        assertEquals(Object.class, handler.getResultType());
    }

    @Test
    void testWildcardOptionalResponseTypeUnwrapped() {
        QueryHandlingMember handler = messageHandler("wildcardOptionalType");
        assertEquals(Object.class, handler.getResultType());
    }

    @Test
    void testUpperBoundWildcardOptionalResponseTypeUnwrapped() {
        QueryHandlingMember handler = messageHandler("upperBoundWildcardOptionalType");
        assertEquals(CharSequence.class, handler.getResultType());
    }

    private QueryHandlingMember messageHandler(String methodName) {
        try {
            MessageHandlingMember<MethodQueryMessageHandlerDefinitionTest> handler = handlerDefinition.createHandler(MethodQueryMessageHandlerDefinitionTest.class, MethodQueryMessageHandlerDefinitionTest.class.getDeclaredMethod(methodName, String.class), parameterResolver).orElseThrow(IllegalArgumentException::new);
            return testSubject.wrapHandler(handler)
                              .unwrap(QueryHandlingMember.class)
                              .orElseThrow(() -> new RuntimeException("Method not recognized as Query Definition"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method used to build query handler does not exist", e);
        }
    }

    @QueryHandler
    private void illegalQueryResponseType(String string) {
    }

    @QueryHandler
    private CompletableFuture<String> futureReturnType(String string) {
        return null;
    }

    @QueryHandler
    private Optional<String> optionalReturnType(String string) {
        return Optional.empty();
    }

    @QueryHandler
    private Optional unspecifiedOptionalType(String string) {
        return Optional.empty();
    }

    @QueryHandler
    private Optional<?> wildcardOptionalType(String string) {
        return Optional.empty();
    }

    @QueryHandler
    private Optional<? extends CharSequence> upperBoundWildcardOptionalType(String string) {
        return Optional.empty();
    }

}
