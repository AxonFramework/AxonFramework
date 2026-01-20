/*
 * Copyright (c) 2010-2026. Axon Framework
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
package org.axonframework.messaging.core.timeout;

import org.axonframework.messaging.core.timeout.AxonTaskJanitor;
import org.axonframework.messaging.core.timeout.TimeoutWrappedMessageHandlingMember;
import org.axonframework.messaging.core.timeout.UnitOfWorkTimeoutInterceptorBuilder;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * The different timeout components, {@link TimeoutWrappedMessageHandlingMember} and
 * {@link UnitOfWorkTimeoutInterceptorBuilder} are tested in isolation, but this test class combines them to ensure that the
 * timeout behavior works as expected when both are used together.
 */
@Disabled("TODO #3559")
class CombinedTimeoutTests {

    @AfterEach
    void tearDown() throws InterruptedException {
        //noinspection ResultOfMethodCallIgnored | Awaiting termination to ensure none of the AxonTimeLimitedTask hang
        AxonTaskJanitor.INSTANCE
                .awaitTermination(250, TimeUnit.MILLISECONDS);
    }

    /**
     * Simple test where the message handling member takes longer than the timeout specified, and the batch fits inside
     * the unit of work timeout.
     */
    @Test
    void onMessageHandlerInterruptWorks() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(100, () -> {
//            Thread.sleep(200);
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(500);
//
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//
//        await().until(uow::isRolledBack);
//        assertInstanceOf(AxonTimeoutException.class, uow.getExecutionResult().getExceptionResult());
//        assertFalse(Thread.interrupted());
    }

    /**
     * Simple test where the unit of work timeout is shorter than the message handling member timeout, so the unit of
     * work timeout should kick in.
     */
    @Test
    void onUnitOfWorkInterruptWorks() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(500, () -> {
//            Thread.sleep(200);
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(100);
//
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//
//        await().until(uow::isRolledBack);
//        assertInstanceOf(AxonTimeoutException.class, uow.getExecutionResult().getExceptionResult());
//        assertFalse(Thread.interrupted());
    }

    @Test
    void handlingMemberInterruptStillWorksIfExceptionIsWrapped() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(100, () -> {
//            try {
//                Thread.sleep(200);
//            } catch (InterruptedException e) {
//                throw new RuntimeException("Wrapped exception", e);
//            }
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(500);
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//        await().until(uow::isRolledBack);
//        assertInstanceOf(AxonTimeoutException.class, uow.getExecutionResult().getExceptionResult());
//        assertFalse(Thread.interrupted());
    }

    @Test
    void handlingMemberInterruptStillWorksIfExceptionIsIgnored() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(100, () -> {
//            try {
//                Thread.sleep(200);
//            } catch (Exception e) {
//                // Ignored
//            }
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(500);
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//        await().until(uow::isRolledBack);
//        assertInstanceOf(AxonTimeoutException.class, uow.getExecutionResult().getExceptionResult());
//        assertFalse(Thread.interrupted());
    }

    @Test
    void unitOfWorkInterruptStillWorksIfExceptionIsWrapped() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(500, () -> {
//            try {
//                Thread.sleep(200);
//            } catch (InterruptedException e) {
//                throw new RuntimeException("Wrapped exception", e);
//            }
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(300);
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//        await().until(uow::isRolledBack);
//        assertInstanceOf(AxonTimeoutException.class, uow.getExecutionResult().getExceptionResult());
//        assertFalse(Thread.interrupted());
    }

    @Test
    void unitOfWorkInterruptStillWorksIfExceptionIsIgnored() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(500, () -> {
//            try {
//                Thread.sleep(200);
//            } catch (Exception e) {
//                // Ignored
//            }
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(300);
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//        await().until(uow::isRolledBack);
//        assertInstanceOf(AxonTimeoutException.class, uow.getExecutionResult().getExceptionResult());
//        assertFalse(Thread.interrupted());
    }

    @Test
    void whenThreadIsInterruptedFromUnrelatedProcessTheInterruptIsPreserved() {
//        TimeoutWrappedMessageHandlingMember<Object> mhm = createMessageHandlingMember(100000, () -> {
//            Thread.sleep(20);
//            Thread.currentThread().interrupt();
//            return null;
//        });
//        UnitOfWorkTimeoutInterceptor interceptor = createTimeoutInterceptor(100000);
//
//        List<EventMessage> batch = generateBatch(2);
//        BatchingUnitOfWork<?> uow = doExecution(batch, interceptor, mhm);
//
//        await().until(uow::isRolledBack);
//        assertInstanceOf(InterruptedException.class, uow.getExecutionResult().getExceptionResult());
//        assertTrue(Thread.interrupted());
    }

//    private List<EventMessage> generateBatch(int size) {
//        List<EventMessage> batch = new LinkedList<>();
//        for (int i = 0; i < size; i++) {
//            batch.add(new GenericEventMessage("TestEvent" + i));
//        }
//        return batch;
//    }

//    private BatchingUnitOfWork<?> doExecution(List<EventMessage> batch,
//                                              UnitOfWorkTimeoutInterceptor interceptor,
//                                              TimeoutWrappedMessageHandlingMember<Object> mhm) {
//        BatchingUnitOfWork<?> uow = new BatchingUnitOfWork<>(batch);
//        uow.executeWithResult(() -> interceptor.handle(uow, () -> mhm.handle(uow.getMessage(), null)));
//        return uow;
//    }

//    private TimeoutWrappedMessageHandlingMember<Object> createMessageHandlingMember(int timeout,
//                                                                                    Callable<Object> callable) {
//        return new TimeoutWrappedMessageHandlingMember<>(
//                new SimpleMessageHandlingMember(callable),
//                timeout,
//                500,
//                100
//        );
//    }

    private UnitOfWorkTimeoutInterceptorBuilder createTimeoutInterceptor(int timeout) {
        return new UnitOfWorkTimeoutInterceptorBuilder(
                "TestComponent",
                timeout,
                500,
                100,
                AxonTaskJanitor.INSTANCE,
                AxonTaskJanitor.LOGGER
        );
    }

//    private static class SimpleMessageHandlingMember implements MessageHandlingMember<Object> {
//
//        private final Callable<Object> callable;
//
//        private SimpleMessageHandlingMember(Callable<Object> callable) {
//            this.callable = callable;
//        }
//
//        @Override
//        public Class<?> payloadType() {
//            return String.class;
//        }
//
//        @Override
//        public boolean canHandle(@Nonnull Message message) {
//            return true;
//        }
//
//        @Override
//        public boolean canHandleMessageType(@Nonnull Class<? extends Message> messageType) {
//            return true;
//        }
//
//        @Override
//        public Object handle(@Nonnull Message message, @Nullable Object target) throws Exception {
//            callable.call();
//            return null;
//        }
//
//        @Override
//        public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
//            return Optional.empty();
//        }
//
//        @Override
//        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
//            return false;
//        }
//
//        @Override
//        public Optional<Map<String, Object>> annotationAttributes(
//                Class<? extends Annotation> annotationType) {
//            return Optional.empty();
//        }
//    }
}
