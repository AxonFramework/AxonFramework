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

package org.axonframework.messaging.core.interception;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.slf4j.Log4jLogger;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.axonframework.messaging.core.MessagingTestUtils.message;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases to verify the logger of a {@link LoggingInterceptor} is called when expected.
 *
 * @author Allard Buijze
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
class LoggingInterceptorTest {

    private LoggingInterceptor<Message> testSubject;
    private ExtendedLogger mockLogger;
    private MessageHandlerInterceptorChain<Message> handlerChain;
    private ProcessingContext context;
    private Message message;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new LoggingInterceptor<>();

        Log4jLogger logger = (Log4jLogger) LoggerFactory.getLogger(LoggingInterceptor.class);
        Field loggerField = logger.getClass().getDeclaredField("logger");
        ReflectionUtils.makeAccessible(loggerField);

        mockLogger = mock(ExtendedLogger.class);
        loggerField.set(logger, mockLogger);

        handlerChain = mock();

        message = message(new StubMessage());
        context = StubProcessingContext.forMessage(message);
    }

    @Test
    void constructorWithCustomLogger() throws Exception {
        testSubject = new LoggingInterceptor<>("my.custom.logger");

        Field field = testSubject.getClass().getDeclaredField("logger");
        field.setAccessible(true);
        org.slf4j.Logger logger = (org.slf4j.Logger) field.get(testSubject);

        assertEquals("my.custom.logger", logger.getName());
    }

    @Test
    void handlerInterceptorWithIncomingLoggingNullReturnValue() {
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        when(handlerChain.proceed(message, context)).thenReturn(MessageStream.empty().cast());

        MessageStream<?> stream = testSubject.interceptOnHandle(message, context, handlerChain);

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), eq("Incoming message: [{}]"), contains("StubMessage"));
        stream.next(); // consume
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    void handlerInterceptorWithSuccessfulExecutionVoidReturnValue() {
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        when(handlerChain.proceed(message, context)).thenReturn(MessageStream.just(message(Void.TYPE.getSimpleName(), null)).cast());

        MessageStream<?> stream = testSubject.interceptOnHandle(message, context, handlerChain);
        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), eq("Incoming message: [{}]"), contains("StubMessage"));

        stream.next(); // consume
        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"), contains("Void"));
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    void handlerInterceptorWithSuccessfulExecutionCustomReturnValue() throws Exception {
        when(handlerChain.proceed(message, context)).thenReturn(MessageStream.just(message(new StubResponse())).cast());
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        MessageStream<?> stream = testSubject.interceptOnHandle(message, context, handlerChain);

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), eq("Incoming message: [{}]"), contains("StubMessage"));
        stream.next(); // consume
        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"), contains("StubResponse"));
        verifyNoMoreInteractions(mockLogger);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    void handlerInterceptorWithFailedExecution() {
        RuntimeException exception = new RuntimeException();
        when(handlerChain.proceed(message, context)).thenReturn(MessageStream.failed(exception).cast());
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        MessageStream<?> stream = testSubject.interceptOnHandle(message, context, handlerChain);
        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));

        stream.next();
        verify(mockLogger).logIfEnabled(anyString(), eq(Level.WARN), isNull(), contains("resulted in an error"), contains("StubMessage"), eq(exception));
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    void dispatchInterceptorLogging() {
        MessageDispatchInterceptorChain<Message> dispatchChain = mock();
        when(dispatchChain.proceed(message, null)).thenReturn(MessageStream.just(message(new StubResponse())).cast());
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        MessageStream<?> stream = testSubject.interceptOnDispatch(message, null, dispatchChain);

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));
        stream.next();
        verifyNoMoreInteractions(mockLogger);
    }

    private static class StubMessage {

    }

    private static class StubResponse {

    }
}
