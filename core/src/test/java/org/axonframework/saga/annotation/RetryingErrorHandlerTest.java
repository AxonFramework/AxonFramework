package org.axonframework.saga.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.StubSaga;
import org.axonframework.testutils.MockException;
import org.junit.*;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class RetryingErrorHandlerTest {

    private final StubSaga saga = new StubSaga("id");
    private RuntimeException exception = new MockException();
    private EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("test");

    @Test
    public void testRetryPolicyOnErrorPreparing() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler(
                new RetryingErrorHandler.TimeoutConfiguration(5, 10, TimeUnit.MILLISECONDS),
                new RetryingErrorHandler.TimeoutConfiguration(2, 2, TimeUnit.SECONDS));

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 5, exception).requiresRescheduleEvent());
        assertEquals(10L, testSubject.onErrorPreparing(Saga.class, null, 5, exception).waitTime());

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 6, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, null, 6, exception).waitTime());

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 7, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, null, 7, exception).waitTime());

        assertFalse(testSubject.onErrorPreparing(Saga.class, eventMessage, 8, exception).requiresRescheduleEvent());
    }

    @Test
    public void testRetryPolicyOnErrorPreparingWithIndefiniteRetries() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler(
                new RetryingErrorHandler.TimeoutConfiguration(5, 10, TimeUnit.MILLISECONDS),
                new RetryingErrorHandler.TimeoutConfiguration(2, TimeUnit.SECONDS));

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 5, exception).requiresRescheduleEvent());
        assertEquals(10L, testSubject.onErrorPreparing(Saga.class, eventMessage, 5, exception).waitTime());

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 6, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, 6, exception).waitTime());

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 7, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, 7, exception).waitTime());

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, Integer.MAX_VALUE, exception)
                              .requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, Integer.MAX_VALUE, exception).waitTime());
    }

    @Test
    public void testDefaultToIndefiniteRetryEveryTwoSeconds() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler();

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, 0, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, 0, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 0, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, 0, exception).waitTime());

        assertTrue(testSubject.onErrorPreparing(Saga.class, eventMessage, Integer.MAX_VALUE, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, Integer.MAX_VALUE, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, Integer.MAX_VALUE, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, Integer.MAX_VALUE, exception).waitTime());
    }

    @Test
    public void testRetryPolicyOnErrorInvoking() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler(
                new RetryingErrorHandler.TimeoutConfiguration(5, 10, TimeUnit.MILLISECONDS),
                new RetryingErrorHandler.TimeoutConfiguration(2, 2, TimeUnit.SECONDS));

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 5, exception).requiresRescheduleEvent());
        assertEquals(10L, testSubject.onErrorPreparing(Saga.class, eventMessage, 5, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 6, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, 6, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 7, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorPreparing(Saga.class, eventMessage, 7, exception).waitTime());

        assertFalse(testSubject.onErrorInvoking(saga, eventMessage, 8, exception).requiresRescheduleEvent());
    }

    @Test
    public void testRetryPolicyOnErrorInvokingWithIndefiniteRetries() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler(
                new RetryingErrorHandler.TimeoutConfiguration(5, 10, TimeUnit.MILLISECONDS),
                new RetryingErrorHandler.TimeoutConfiguration(2, TimeUnit.SECONDS));

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 5, exception).requiresRescheduleEvent());
        assertEquals(10L, testSubject.onErrorInvoking(saga, eventMessage, 5, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 6, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorInvoking(saga, eventMessage, 6, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, 7, exception).requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorInvoking(saga, eventMessage, 7, exception).waitTime());

        assertTrue(testSubject.onErrorInvoking(saga, eventMessage, Integer.MAX_VALUE, exception)
                              .requiresRescheduleEvent());
        assertEquals(2000L, testSubject.onErrorInvoking(saga, eventMessage, Integer.MAX_VALUE, exception).waitTime());
    }

    @Test
    public void testProceedWhenPrepareCausesNonTransientException() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler(
                new RetryingErrorHandler.TimeoutConfiguration(5, 10, TimeUnit.MILLISECONDS),
                new RetryingErrorHandler.TimeoutConfiguration(2, TimeUnit.SECONDS));

        assertFalse(testSubject.onErrorPreparing(Saga.class, eventMessage, 0, new AxonConfigurationException("Mock"))
                               .requiresRescheduleEvent());
        assertFalse(testSubject.onErrorPreparing(Saga.class,
                                                 eventMessage,
                                                 Integer.MAX_VALUE,
                                                 new AxonConfigurationException("Mock"))
                               .requiresRescheduleEvent());
    }

    @Test
    public void testProceedWhenInvokeCausesNonTransientException() throws Exception {
        RetryingErrorHandler testSubject = new RetryingErrorHandler(
                new RetryingErrorHandler.TimeoutConfiguration(5, 10, TimeUnit.MILLISECONDS),
                new RetryingErrorHandler.TimeoutConfiguration(2, TimeUnit.SECONDS));

        assertFalse(testSubject.onErrorInvoking(saga, eventMessage, 0, new AxonConfigurationException("Mock"))
                               .requiresRescheduleEvent());
        assertFalse(testSubject.onErrorInvoking(saga, eventMessage, Integer.MAX_VALUE, new AxonConfigurationException("Mock"))
                               .requiresRescheduleEvent());
    }
}