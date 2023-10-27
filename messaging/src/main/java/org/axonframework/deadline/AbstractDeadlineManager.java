/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.deadline;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

/**
 * Abstract implementation of the {@link DeadlineManager} to be implemented by concrete solutions for the
 * DeadlineManager. Provides functionality to perform a call to the DeadlineManager in the a {@link UnitOfWork} it's
 * 'prepare commit' phase. This #runOnPrepareCommitOrNow(Runnable) functionality is required, as the DeadlineManager
 * schedules a Message which needs to happen on order with the other messages published throughout the system.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public abstract class AbstractDeadlineManager implements DeadlineManager {

    private final List<MessageDispatchInterceptor<? super DeadlineMessage<?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();
    private final List<MessageHandlerInterceptor<? super DeadlineMessage<?>>> handlerInterceptors = new CopyOnWriteArrayList<>();

    /**
     * Run a given {@code deadlineCall} immediately, or schedule it for the {@link UnitOfWork} it's 'prepare commit'
     * phase if a UnitOfWork is active. This is required as the DeadlineManager schedule message which we want to happen
     * on order with other message being handled.
     *
     * @param deadlineCall a {@link Runnable} to be executed now or on prepare commit if a {@link UnitOfWork} is active
     */
    protected void runOnPrepareCommitOrNow(Runnable deadlineCall) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().onPrepareCommit(unitOfWork -> deadlineCall.run());
        } else {
            deadlineCall.run();
        }
    }

    @Override
    public Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super DeadlineMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public Registration registerHandlerInterceptor(
            @Nonnull MessageHandlerInterceptor<? super DeadlineMessage<?>> handlerInterceptor) {
        handlerInterceptors.add(handlerInterceptor);
        return () -> handlerInterceptors.remove(handlerInterceptor);
    }

    /**
     * Provides a list of registered dispatch interceptors. Do note that this list is not modifiable, and that changes
     * in the internal structure for dispatch interceptors will be reflected in this list.
     *
     * @return a list of dispatch interceptors
     */
    protected List<MessageDispatchInterceptor<? super DeadlineMessage<?>>> dispatchInterceptors() {
        return Collections.unmodifiableList(dispatchInterceptors);
    }

    /**
     * Provides a list of registered handler interceptors. Do note that this list is not modifiable, and that changes
     * in the internal structure for handler interceptors will be reflected in this list.
     *
     * @return a list of handler interceptors
     */
    protected List<MessageHandlerInterceptor<? super DeadlineMessage<?>>> handlerInterceptors() {
        return Collections.unmodifiableList(handlerInterceptors);
    }

    /**
     * Applies registered {@link MessageDispatchInterceptor}s to the given {@code message}.
     *
     * @param message the deadline message to be intercepted
     * @param <T>     the type of deadline message payload
     * @return intercepted message
     */
    @SuppressWarnings("unchecked")
    protected <T> DeadlineMessage<T> processDispatchInterceptors(DeadlineMessage<T> message) {
        DeadlineMessage<T> intercepted = message;
        for (MessageDispatchInterceptor<? super DeadlineMessage<?>> interceptor : dispatchInterceptors()) {
            intercepted = (DeadlineMessage<T>) interceptor.handle(intercepted);
        }
        return intercepted;
    }
}
