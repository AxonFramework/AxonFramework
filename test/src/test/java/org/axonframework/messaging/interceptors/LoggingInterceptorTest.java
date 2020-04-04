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

package org.axonframework.messaging.interceptors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.slf4j.Log4jLogger;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.Mockito.*;

/**
 * Test cases to verify the logger of a {@link LoggingInterceptor} is called when expected.
 *
 * @author Allard Buijze
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
class LoggingInterceptorTest {

    private LoggingInterceptor<Message<?>> testSubject;
    private ExtendedLogger mockLogger;
    private InterceptorChain interceptorChain;
    private UnitOfWork<Message<?>> unitOfWork;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new LoggingInterceptor<>();

        Log4jLogger logger = (Log4jLogger) LoggerFactory.getLogger(LoggingInterceptor.class);
        Field loggerField = logger.getClass().getDeclaredField("logger");
        ReflectionUtils.ensureAccessible(loggerField);

        mockLogger = mock(ExtendedLogger.class);
        loggerField.set(logger, mockLogger);

        interceptorChain = mock(InterceptorChain.class);
        unitOfWork = new DefaultUnitOfWork<>(new GenericMessage<Object>(new StubMessage()));
    }

    @Test
    void testConstructorWithCustomLogger() throws Exception {
        testSubject = new LoggingInterceptor<>("my.custom.logger");

        Field field = testSubject.getClass().getDeclaredField("logger");
        field.setAccessible(true);
        org.slf4j.Logger logger = (org.slf4j.Logger) field.get(testSubject);

        assertEquals("my.custom.logger", logger.getName());
    }

    @Test
    void testHandlerInterceptorWithIncomingLoggingNullReturnValue() throws Exception {
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        when(interceptorChain.proceed()).thenReturn(null);

        testSubject.handle(unitOfWork, interceptorChain);

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));
        verify(mockLogger).logIfEnabled(
                anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"), contains("null")
        );
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    void testHandlerInterceptorWithSuccessfulExecutionVoidReturnValue() throws Exception {
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        when(interceptorChain.proceed()).thenReturn(null);

        testSubject.handle(unitOfWork, interceptorChain);

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));
        verify(mockLogger).logIfEnabled(
                anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"), contains("null")
        );
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    void testHandlerInterceptorWithSuccessfulExecutionCustomReturnValue() throws Exception {
        when(interceptorChain.proceed()).thenReturn(new StubResponse());
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        testSubject.handle(unitOfWork, interceptorChain);

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));
        verify(mockLogger).logIfEnabled(
                anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"), contains("StubResponse")
        );
        verifyNoMoreInteractions(mockLogger);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    void testHandlerInterceptorWithFailedExecution() throws Exception {
        RuntimeException exception = new RuntimeException();
        when(interceptorChain.proceed()).thenThrow(exception);
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        try {
            testSubject.handle(unitOfWork, interceptorChain);
            fail("Expected exception to be propagated");
        } catch (RuntimeException e) {
            // expected
        }

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));
        verify(mockLogger).logIfEnabled(
                anyString(), eq(Level.WARN), isNull(), and(contains("failed"), contains("StubMessage")), eq(exception)
        );
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    void testDispatchInterceptorLogging() {
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        testSubject.handle(new GenericMessage<Object>(new StubMessage()));

        verify(mockLogger).logIfEnabled(anyString(), eq(Level.INFO), isNull(), anyString(), contains("StubMessage"));
        verifyNoMoreInteractions(mockLogger);
    }

    private static class StubMessage {

    }

    private static class StubResponse {

    }
}
