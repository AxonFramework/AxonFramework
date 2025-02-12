package org.axonframework.messaging.timeout;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class UnitOfWorkTimeoutInterceptorTest {

    @Test
    void interruptsUnitOfWorkThatTakesTooLong() throws Exception {
        UnitOfWorkTimeoutInterceptor testSubject = new UnitOfWorkTimeoutInterceptor("MyUnitOfWork", 100, 50, 10);

        DefaultUnitOfWork<EventMessage<String>> uow = new DefaultUnitOfWork<>(
                GenericEventMessage.asEventMessage("test")
        );
        DefaultInterceptorChain<EventMessage<String>> interceptorChain = new DefaultInterceptorChain<>(
                uow,
                Collections.singletonList(testSubject),
                message -> {
                    Thread.sleep(300);
                    return null;
                });
        uow.executeWithResult(interceptorChain::proceed);
        assertTrue(uow.isRolledBack());
        assertTrue(uow.getExecutionResult().isExceptionResult());
        assertInstanceOf(InterruptedException.class, uow.getExecutionResult().getExceptionResult());
    }


    @Test
    void doesNotInterruptWorkWithinTime() throws Exception {
        UnitOfWorkTimeoutInterceptor testSubject = new UnitOfWorkTimeoutInterceptor("MyUnitOfWork", 100, 50, 10);

        DefaultUnitOfWork<EventMessage<String>> uow = new DefaultUnitOfWork<>(
                GenericEventMessage.asEventMessage("test")
        );
        DefaultInterceptorChain<EventMessage<String>> interceptorChain = new DefaultInterceptorChain<>(
                uow,
                Collections.singletonList(testSubject),
                message -> {
                    Thread.sleep(80);
                    return null;
                });
        uow.executeWithResult(interceptorChain::proceed);
        assertFalse(uow.isRolledBack());
        assertFalse(uow.getExecutionResult().isExceptionResult());
    }
}