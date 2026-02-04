/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.axonframework.messaging.queryhandling.annotation;

import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.core.interception.annotation.ExceptionHandler;
import org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.QueryHandlingComponent;
import org.axonframework.messaging.queryhandling.QueryMessage;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.conversion.PassThroughConverter;
import org.axonframework.common.util.MockException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotatedQueryHandlingComponent}.
 *
 * @author Marc Gathier
 * @author Steven van Beelen
 */
@Disabled("TODO #3485")
class InterceptingAnnotatedQueryHandlingComponentTest {

    @Test
    void interceptMessages() {
        // given...
        List<QueryMessage> withInterceptor = new ArrayList<>();
        List<QueryMessage> withoutInterceptor = new ArrayList<>();
        MyInterceptingQueryHandler handler = new MyInterceptingQueryHandler(withoutInterceptor, withInterceptor, new ArrayList<>());
        QueryHandlingComponent testSubject = new AnnotatedQueryHandlingComponent<>(
                handler,
                ClasspathParameterResolverFactory.forClass(handler.getClass()),
                ClasspathHandlerDefinition.forClass(handler.getClass()),
                new AnnotationMessageTypeResolver(),
                new DelegatingMessageConverter(PassThroughConverter.INSTANCE)
        );

        QueryMessage testQuery = new GenericQueryMessage(new MessageType("echo"), "Hi");
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        String result = testSubject.handle(testQuery, testContext)
                                   .first()
                                   .asCompletableFuture()
                                   .thenApply(MessageStream.Entry::message)
                                   .thenApply(m -> m.payloadAs(String.class))
                                   .join();
        // then...
        assertEquals("Hi", result);
        assertEquals(Collections.singletonList(testQuery), withInterceptor);
        assertEquals(Collections.singletonList(testQuery), withoutInterceptor);
    }

    @Test
    void exceptionHandlerAnnotatedMethodsAreSupportedForQueryHandlingComponents() {
        // given...
        List<Exception> interceptedExceptions = new ArrayList<>();
        MyInterceptingQueryHandler handler = new MyInterceptingQueryHandler(new ArrayList<>(), new ArrayList<>(), interceptedExceptions);
        QueryHandlingComponent testSubject = new AnnotatedQueryHandlingComponent<>(
                handler,
                ClasspathParameterResolverFactory.forClass(handler.getClass()),
                ClasspathHandlerDefinition.forClass(handler.getClass()),
                new AnnotationMessageTypeResolver(),
                new DelegatingMessageConverter(PassThroughConverter.INSTANCE)
        );

        QueryMessage testQuery = new GenericQueryMessage(new MessageType("faulty"),
                                                         "hello");
        ProcessingContext testContext = StubProcessingContext.forMessage(testQuery);
        // when...
        MessageStream<QueryResponseMessage> result = testSubject.handle(testQuery, testContext);
        // then...
        assertThat(result.hasNextAvailable()).isTrue();
        Optional<Throwable> resultError = result.error();
        assertThat(resultError).isNotPresent();
        assertFalse(interceptedExceptions.isEmpty());
        assertEquals(1, interceptedExceptions.size());
        Exception interceptedException = interceptedExceptions.getFirst();
        assertInstanceOf(RuntimeException.class, interceptedException);
        assertEquals("Some exception", interceptedException.getMessage());
    }

    private record MyInterceptingQueryHandler(
            List<QueryMessage> interceptedWithoutInterceptorChain,
            List<QueryMessage> interceptedWithInterceptorChain,
            List<Exception> interceptedExceptions
    ) {

        @SuppressWarnings("unused")
        @MessageHandlerInterceptor
        public void interceptAny(QueryMessage query) {
            interceptedWithoutInterceptorChain.add(query);
        }

        @SuppressWarnings("unused")
        @MessageHandlerInterceptor
        public Object interceptAny(QueryMessage query,
                                   MessageHandlerInterceptorChain<QueryMessage> chain,
                                   ProcessingContext context) {
            interceptedWithInterceptorChain.add(query);
            return chain.proceed(query, context);
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "echo")
        public String echo(String echo) {
            return echo;
        }

        @SuppressWarnings("unused")
        @QueryHandler(queryName = "faulty")
        public Integer faulty(String echo) {
            throw new MockException("Mock");
        }

        @SuppressWarnings("unused")
        @ExceptionHandler(resultType = MockException.class)
        public void handle(RuntimeException exception) {
            interceptedExceptions.add(exception);
        }
    }
}
