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

package org.axonframework.queryhandling.annotation;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.messaging.QualifiedName.className;
import static org.axonframework.messaging.QualifiedName.dottedName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the {@link MethodQueryMessageHandlerDefinition} correctly deals with return types, as well as for
 * example {@link java.util.concurrent.Future} and {@link Optional} which contain a generic type.
 *
 * @author Allard Buijze
 */
class MethodQueryMessageHandlerDefinitionTest {

    private MethodQueryMessageHandlerDefinition testSubject;
    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new MethodQueryMessageHandlerDefinition();
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Test
    void voidNotAcceptedAsReturnType() {
        assertThrows(UnsupportedHandlerException.class, () -> messageHandler("illegalQueryResponseType"));
    }

    @Test
    void futureResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("futureReturnType");
        assertEquals(String.class, handler.getResultType());
    }

    @Test
    void optionalResponseTypeUnwrapped() throws Exception {
        QueryHandlingMember<MethodQueryMessageHandlerDefinitionTest> handler = messageHandler("optionalReturnType");
        assertEquals(String.class, handler.getResultType());

        GenericQueryMessage<String, String> message =
                new GenericQueryMessage<>(dottedName("test.query"), "mock", ResponseTypes.instanceOf(String.class));

        assertTrue(handler.canHandle(message, null));

        Object invocationResult = handler.handleSync(message, this);
        assertNull(invocationResult);
    }

    @Test
    void unspecifiedOptionalResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("unspecifiedOptionalType");
        assertEquals(Object.class, handler.getResultType());
    }

    @Test
    void wildcardOptionalResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("wildcardOptionalType");
        assertEquals(Object.class, handler.getResultType());
    }

    @Test
    void upperBoundWildcardOptionalResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("upperBoundWildcardOptionalType");
        assertEquals(CharSequence.class, handler.getResultType());
    }

    // TODO This local static function should be replaced with a dedicated interface that converts types.
    // TODO However, that's out of the scope of the unit-of-rework branch and thus will be picked up later.
    private static MessageStream<?> returnTypeConverter(Object result) {
        if (result instanceof CompletableFuture<?> future) {
            return MessageStream.fromFuture(future.thenApply(r -> new GenericMessage<>(className(r.getClass()), r)));
        }
        return MessageStream.just(new GenericMessage<>(className(result.getClass()), result));
    }

    private <R> QueryHandlingMember<R> messageHandler(String methodName) {
        try {
            MessageHandlingMember<MethodQueryMessageHandlerDefinitionTest> handler = handlerDefinition.createHandler(
                    MethodQueryMessageHandlerDefinitionTest.class,
                    MethodQueryMessageHandlerDefinitionTest.class.getDeclaredMethod(methodName, String.class),
                    parameterResolver,
                    MethodQueryMessageHandlerDefinitionTest::returnTypeConverter
            ).orElseThrow(IllegalArgumentException::new);
            //noinspection unchecked
            return testSubject.wrapHandler(handler)
                              .unwrap(QueryHandlingMember.class)
                              .orElseThrow(() -> new RuntimeException("Method not recognized as Query Definition"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method used to build query handler does not exist", e);
        }
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private void illegalQueryResponseType(String string) {
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private CompletableFuture<String> futureReturnType(String string) {
        return null;
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private Optional<String> optionalReturnType(String string) {
        return Optional.empty();
    }

    @SuppressWarnings({"unused", "rawtypes"})
    @QueryHandler
    private Optional unspecifiedOptionalType(String string) {
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private Optional<?> wildcardOptionalType(String string) {
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private Optional<? extends CharSequence> upperBoundWildcardOptionalType(String string) {
        return Optional.empty();
    }
}
