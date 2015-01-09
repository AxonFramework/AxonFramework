package org.axonframework.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Timer;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.testutils.MockException;
import org.junit.*;

import java.util.Arrays;
import java.util.Set;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class InstrumentedCommandBusTest {

    private InstrumentedCommandBus testSubject;
    private CommandBus delegate;

    @Before
    public void setUp() throws Exception {
        delegate = new SimpleCommandBus();
        testSubject = new InstrumentedCommandBus(delegate);
        testSubject.subscribe(String.class.getName(), (m, u) -> m.getPayload());
        delegate.subscribe(Long.class.getName(), (m, u) -> {
            throw new MockException();
        });
    }

    @Test
    public void testSupportedCommandsReported() throws Exception {
        Gauge<Set<String>> supportedCommands = testSubject.getSupportedCommands();
        @SuppressWarnings("unchecked")
        final Set<String> supportedCommandsValue = supportedCommands.getValue();
        assertEquals(1, supportedCommandsValue.size());
        assertEquals(String.class.getName(), supportedCommandsValue.iterator().next());

        final CommandHandler<Object> handler = (m, u) -> null;
        testSubject.subscribe(Long.class.getName(), handler);

        assertEquals(1, supportedCommandsValue.size());
        assertEquals(2, supportedCommands.getValue().size());

        testSubject.unsubscribe(Long.class.getName(), handler);
        assertEquals(1, supportedCommands.getValue().size());
    }

    @Test
    public void testFailureCounterUpdateWhenDispatchingFailedCommands_FireForget() throws Exception {
        Counter failureCounter = testSubject.getFailureCounter();
        Counter successCounter = testSubject.getSuccessCounter();

        assertEquals(0, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());

        testSubject.dispatch(asCommandMessage(1L));

        assertEquals(1, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());
    }

    @Test
    public void testFailureCounterUpdateWhenErrorInDispatch() throws Exception {
        final CommandBus mockCommandBus = mock(CommandBus.class);
        testSubject = new InstrumentedCommandBus(mockCommandBus);
        final Counter handlingCounter = testSubject.getHandlingCounter();
        final Timer commandTimer = testSubject.getCommandTimer();
        doThrow(new MockException()).when(mockCommandBus).dispatch(any(), any());
        Counter failureCounter = testSubject.getFailureCounter();
        Counter successCounter = testSubject.getSuccessCounter();

        assertEquals(0, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());

        try {
            testSubject.dispatch(asCommandMessage(1L));
            fail("expected exception to be propagated");
        } catch (MockException e) {
            //expected
        }

        assertEquals(1, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());
        assertEquals(0, handlingCounter.getCount());
        assertEquals(1, commandTimer.getCount());
    }

    @Test
    public void testSuccessCounterUpdateWhenDispatchingCommands_FireForget() throws Exception {
        Counter failureCounter = testSubject.getFailureCounter();
        Counter successCounter = testSubject.getSuccessCounter();

        assertEquals(0, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());

        testSubject.dispatch(asCommandMessage("success"));

        assertEquals(0, failureCounter.getCount());
        assertEquals(1, successCounter.getCount());
    }

    @Test
    public void testFailureCounterUpdateWhenDispatchingFailedCommands_WithCallback() throws Exception {
        Counter failureCounter = testSubject.getFailureCounter();
        Counter successCounter = testSubject.getSuccessCounter();

        assertEquals(0, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());

        testSubject.dispatch(asCommandMessage(1L), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Didn't expect success");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertNotNull(cause);
            }
        });

        assertEquals(1, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());
    }

    @Test
    public void testSuccessCounterUpdateWhenDispatchingCommands_WithCallback() throws Exception {
        Counter failureCounter = testSubject.getFailureCounter();
        Counter successCounter = testSubject.getSuccessCounter();

        assertEquals(0, failureCounter.getCount());
        assertEquals(0, successCounter.getCount());

        testSubject.dispatch(asCommandMessage("success"), new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                assertEquals("success", result);
            }

            @Override
            public void onFailure(Throwable cause) {
                fail("Didn't expect failure");
            }
        });

        assertEquals(0, failureCounter.getCount());
        assertEquals(1, successCounter.getCount());
    }

    @Test
    public void testHandlingCounterUpdated() throws Exception {
        testSubject.subscribe(String.class.getName(), (commandMessage, unitOfWork) -> {
            assertEquals(1, testSubject.getHandlingCounter().getCount());
            return commandMessage.getPayload();
        });

        assertEquals(0, testSubject.getHandlingCounter().getCount());
        testSubject.dispatch(asCommandMessage("test"));
        assertEquals(0, testSubject.getHandlingCounter().getCount());
        assertEquals(1, testSubject.getCommandTimer().getCount());
        assertNotEquals(0, testSubject.getCommandTimer().getFifteenMinuteRate());
        assertNotEquals(0, testSubject.getCommandTimer().getOneMinuteRate());
        assertNotEquals(0, testSubject.getCommandTimer().getFiveMinuteRate());
    }

    @Test
    public void testMetricsRegisteredInMetricSet() throws Exception {
        testSubject.getMetricSet().getMetrics().keySet()
                   .containsAll(Arrays.asList("supported-commands", "handling", "success",
                                              "failure", "command-response-time"));
    }
}