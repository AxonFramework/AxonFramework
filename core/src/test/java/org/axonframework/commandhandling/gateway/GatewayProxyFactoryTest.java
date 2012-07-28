package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.annotation.MetaData;
import org.hamcrest.Description;
import org.junit.*;
import org.junit.internal.matchers.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
public class GatewayProxyFactoryTest {

    private static CommandBus mockCommandBus;
    private static GatewayProxyFactory testSubject;
    private static CompleteGateway gateway;

    @BeforeClass
    public static void beforeClass() {
        mockCommandBus = mock(CommandBus.class);
        testSubject = new GatewayProxyFactory(mockCommandBus);
        gateway = testSubject.createGateway(CompleteGateway.class);
    }

    @Before
    public void setUp() {
        reset(mockCommandBus);
    }

    @Test(timeout = 2000)
    public void testGateway_FireAndForget() {
        final Object metaTest = new Object();
        gateway.fireAndForget("Command", metaTest, "value");
        verify(mockCommandBus).dispatch(argThat(new TypeSafeMatcher<CommandMessage<?>>() {
            @Override
            public boolean matchesSafely(CommandMessage<?> item) {
                return item.getMetaData().get("test") == metaTest
                        && "value".equals(item.getMetaData().get("key"));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("A command with 2 meta data entries");
            }
        }), isA(CommandCallback.class));
    }

    @Test(timeout = 2000)
    public void testGateway_Timeout() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new CountDown(cdl)).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                   isA(CommandCallback.class));
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                gateway.fireAndWait("Command");
            }
        });
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        assertTrue(t.isAlive());
        t.interrupt();
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValue_Returns() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<String>();
        doAnswer(new Success(cdl, "ReturnValue")).when(mockCommandBus).dispatch(isA(CommandMessage.class), isA(
                CommandCallback.class));
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                result.set(gateway.waitForReturnValue("Command"));
            }
        });
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertEquals("ReturnValue", result.get());
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValue_UndeclaredException() throws InterruptedException {
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        doAnswer(new Failure(cdl, new ExpectedException())).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                          isA(CommandCallback.class));
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(gateway.waitForReturnValue("Command"));
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        assertTrue("Expected command bus to be invoked", cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof CommandExecutionException);
        assertTrue(error.get().getCause() instanceof ExpectedException);
    }

    @Test(timeout = 2000)
    public void testGatewayWithReturnValue_Interrupted() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(gateway.waitForReturnValue("Command"));
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertNull(error.get());
    }

    @Test(timeout = 2000)
    public void testGatewayWaitForException_Interrupted() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.waitForException("Command");
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof InterruptedException);
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_Returns() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new Success(cdl, "OK!")).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                        isA(CommandCallback.class));
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.MILLISECONDS);
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        assertTrue(cdl.await(1, TimeUnit.SECONDS));
        t.interrupt();
        // the return type is void, so return value is ignored
        assertNull("Did not expect ReturnValue", result.get());
        assertNull("Did not expect exception", error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_Timeout() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.MILLISECONDS);
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertNull("Did not expect exception", error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_TimeoutException() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.fireAndWaitWithTimeoutParameterAndException("Command", 1, TimeUnit.MILLISECONDS);
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof TimeoutException);
    }

    @Test(timeout = 2000)
    public void testFireAndWaitWithTimeoutParameter_Interrupted() throws InterruptedException {
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.fireAndWaitWithTimeoutParameter("Command", 1, TimeUnit.SECONDS);
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        t.interrupt();
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertNull("Did not expect exception", error.get());
    }

    @Test(timeout = 2000)
    public void testFireAndWaitForCheckedException() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        doAnswer(new Failure(cdl, new ExpectedException())).when(mockCommandBus).dispatch(isA(CommandMessage.class),
                                                                                          isA(CommandCallback.class));
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    gateway.fireAndWaitForCheckedException("Command");
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        assertTrue(cdl.await(1, TimeUnit.SECONDS));
        t.join();
        assertNull("Did not expect ReturnValue", result.get());
        assertTrue(error.get() instanceof ExpectedException);
    }

    @Test(timeout = 2000)
    public void testFireAndGetFuture() throws InterruptedException {
        final AtomicReference<Future<Object>> result = new AtomicReference<Future<Object>>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final CompleteGateway gateway = testSubject.createGateway(CompleteGateway.class);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(gateway.fireAndGetFuture("Command"));
                } catch (Throwable e) {
                    error.set(e);
                }
            }
        });
        t.start();
        t.join();
        assertNotNull("Expected to get a Future return value", result.get());
        assertNull(error.get());
    }

    @Test(timeout = 2000)
    public void testCreateGateway_EqualsAndHashCode() {
        CompleteGateway gateway1 = testSubject.createGateway(CompleteGateway.class);
        CompleteGateway gateway2 = testSubject.createGateway(CompleteGateway.class);

        assertNotSame(gateway1, gateway2);
        assertFalse(gateway1.equals(gateway2));
        assertNotNull(gateway1.hashCode());
        assertNotNull(gateway2.hashCode());
    }

    private static interface CompleteGateway {

        void fireAndForget(Object command, @MetaData("test") Object metaTest, @MetaData("key") Object metaKey);

        String waitForReturnValue(Object command);

        void waitForException(Object command) throws InterruptedException;

        @Timeout(value = 1, unit = TimeUnit.SECONDS)
        void fireAndWait(Object command);

        void fireAndWaitWithTimeoutParameter(Object command, long timeout, TimeUnit unit);

        Object fireAndWaitWithTimeoutParameterAndException(Object command, long timeout, TimeUnit unit)
                throws TimeoutException;

        Object fireAndWaitForCheckedException(Object command) throws ExpectedException;

        Future<Object> fireAndGetFuture(Object command);
    }

    public static class ExpectedException extends Exception {

    }

    private static class CountDown implements Answer {

        private final CountDownLatch cdl;

        public CountDown(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            cdl.countDown();
            return null;
        }
    }

    private static class Success implements Answer {

        private final CountDownLatch cdl;
        private final String returnValue;

        public Success(CountDownLatch cdl, String returnValue) {
            this.cdl = cdl;
            this.returnValue = returnValue;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            cdl.countDown();
            ((CommandCallback) invocation.getArguments()[1]).onSuccess(returnValue);
            return null;
        }
    }

    private class Failure implements Answer {

        private final CountDownLatch cdl;
        private final ExpectedException e;

        public Failure(CountDownLatch cdl, ExpectedException e) {
            this.cdl = cdl;
            this.e = e;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            cdl.countDown();
            ((CommandCallback) invocation.getArguments()[1]).onFailure(e);
            return null;
        }
    }
}
