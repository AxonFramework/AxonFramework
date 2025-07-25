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
package org.axonframework.messaging.timeout;

import org.axonframework.messaging.Context;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;

import java.util.concurrent.ScheduledExecutorService;
import jakarta.annotation.Nonnull;

/**
 * Message handler interceptor that sets a timeout on the processing of the current {@link LegacyUnitOfWork}. If the
 * timeout is reached, the thread is interrupted and the transaction will be rolled back automatically.
 * <p>
 * Note: Due to interceptor ordering, this interceptor may not be the first in the chain. We are unable to work around
 * this, and as such the timeout measuring starts from the moment this interceptor is invoked, and ends measuring when
 * the commit of the {@link LegacyUnitOfWork} is completed.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
public class UnitOfWorkTimeoutInterceptor implements MessageHandlerInterceptor<Message<?>> {

    private static final String TRANSACTION_TIME_LIMIT_RESOURCE_KEY = "_transactionTimeLimit";
    private static final Context.ResourceKey<AxonTimeLimitedTask> TRANSACTION_TIME_LIMIT_CONTEXT_RESOURCE_KEY =
            Context.ResourceKey.withLabel(TRANSACTION_TIME_LIMIT_RESOURCE_KEY);

    private final String componentName;
    private final int timeout;
    private final int warningThreshold;
    private final int warningInterval;
    private final ScheduledExecutorService executorService;
    private final Logger logger;

    /**
     * Creates a new {@link UnitOfWorkTimeoutInterceptor} for the given {@code componentName} with the given
     * {@code timeout}, {@code warningThreshold} and {@code warningInterval}. The warnings and timeout will be scheduled
     * on the {@link AxonTaskJanitor#INSTANCE}. If you want to use a different {@link ScheduledExecutorService} or
     * {@link Logger} to log on, use the other
     * {@link #UnitOfWorkTimeoutInterceptor(String, int, int, int, ScheduledExecutorService, Logger)}.
     *
     * @param componentName    The name of the component to be included in the logging
     * @param timeout          The timeout in milliseconds
     * @param warningThreshold The threshold in milliseconds after which a warning is logged. Setting this to a value
     *                         higher than {@code timeout} will disable warnings.
     * @param warningInterval  The interval in milliseconds between warnings.
     */
    public UnitOfWorkTimeoutInterceptor(String componentName,
                                        int timeout,
                                        int warningThreshold,
                                        int warningInterval
    ) {
        this(componentName,
             timeout,
             warningThreshold,
             warningInterval,
             AxonTaskJanitor.INSTANCE,
             AxonTaskJanitor.LOGGER);
    }

    /**
     * Creates a new {@link UnitOfWorkTimeoutInterceptor} for the given {@code componentName} with the given
     * {@code timeout}, {@code warningThreshold} and {@code warningInterval}. The warnings and timeout will be scheduled
     * on the provided {@code executorService}.
     *
     * @param componentName    The name of the component to be included in the logging
     * @param timeout          The timeout in milliseconds
     * @param warningThreshold The threshold in milliseconds after which a warning is logged. Setting this to a value
     *                         higher than {@code timeout} will disable warnings.
     * @param warningInterval  The interval in milliseconds between warnings.
     * @param executorService  The executor service to schedule the timeout and warnings
     * @param logger           The logger to log warnings and errors
     */
    public UnitOfWorkTimeoutInterceptor(String componentName,
                                        int timeout,
                                        int warningThreshold,
                                        int warningInterval,
                                        ScheduledExecutorService executorService,
                                        Logger logger
    ) {
        this.componentName = componentName;
        this.timeout = timeout;
        this.warningThreshold = warningThreshold;
        this.warningInterval = warningInterval;
        this.executorService = executorService;
        this.logger = logger;
    }

    @Override
    public Object handle(@Nonnull LegacyUnitOfWork<? extends Message<?>> unitOfWork,
                         @Nonnull ProcessingContext context,
                         @Nonnull InterceptorChain interceptorChain) throws Exception {
        LegacyUnitOfWork<?> root = unitOfWork.root();
        String taskName = "UnitOfWork of " + componentName;
        if (!root.resources().containsKey(TRANSACTION_TIME_LIMIT_RESOURCE_KEY)) {
            AxonTimeLimitedTask taskTimeout = taskTimeout(taskName);
            root.resources().put(TRANSACTION_TIME_LIMIT_RESOURCE_KEY, taskTimeout);
            taskTimeout.start();
            unitOfWork.afterCommit(u -> completeSafely(taskTimeout));
            unitOfWork.onRollback(u -> taskTimeout.complete());
        }

        AxonTimeLimitedTask task = (AxonTimeLimitedTask) root.resources().get(TRANSACTION_TIME_LIMIT_RESOURCE_KEY);
        try {
            Object proceed = interceptorChain.proceed();
            task.ensureNoInterruptionWasSwallowed();
            return proceed;
        } catch (Exception e) {
            throw task.detectInterruptionInsteadOfException(e);
        }
    }

    @Override
    public <M extends Message<?>, R extends Message<?>> MessageStream<R> interceptOnHandle(@Nonnull M message,
                                                                                           @Nonnull ProcessingContext context,
                                                                                           @Nonnull InterceptorChain<M, R> interceptorChain) {
        String taskName = "UnitOfWork of " + componentName;
        if (!context.containsResource(TRANSACTION_TIME_LIMIT_CONTEXT_RESOURCE_KEY)) {
            AxonTimeLimitedTask taskTimeout = taskTimeout(taskName);
            context.putResource(TRANSACTION_TIME_LIMIT_CONTEXT_RESOURCE_KEY, taskTimeout);
            taskTimeout.start();
            context.runOnAfterCommit(u -> taskTimeout.complete());
            context.onError((ctx, phase, error) -> taskTimeout.complete());
        }

        AxonTimeLimitedTask task = (AxonTimeLimitedTask) root.resources().get(TRANSACTION_TIME_LIMIT_RESOURCE_KEY);
        try {
            Object proceed = interceptorChain.proceed();
            task.ensureNoInterruptionWasSwallowed();
            return proceed;
        } catch (Exception e) {
            throw task.detectInterruptionInsteadOfException(e);
        }
    }

    private static void completeSafely(AxonTimeLimitedTask task) {
        try {
            try {
                task.ensureNoInterruptionWasSwallowed();
                task.complete();
            } catch (Exception e) {
                throw task.detectInterruptionInsteadOfException(e);
            }
        } catch (Exception e) {
            if(e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private AxonTimeLimitedTask taskTimeout(String taskName) {
        return new AxonTimeLimitedTask(
                taskName,
                timeout,
                warningThreshold,
                warningInterval,
                executorService,
                logger
        );
    }
}
