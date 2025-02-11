package org.axonframework.messaging.timeout;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nonnull;

/**
 * Message handler interceptor that sets a timeout on the processing of the current {@link UnitOfWork}. If the timeout
 * is reached, the thread is interrupted and the transaction will be rolled back automatically.
 * <p>
 * Note: Due to interceptor ordering, this interceptor may not be the first in the chain. We are unable to work around
 * this, and as such the timeout measuring starts from the moment this interceptor is invoked, and ends measuring when
 * the commit of the {@link UnitOfWork} is completed.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
public class UnitOfWorkTimeoutInterceptor implements MessageHandlerInterceptor<Message<?>> {

    private final String componentName;
    private final int timeout;
    private final int warningThreshold;
    private final int warningInterval;
    private final ScheduledExecutorService executorService;

    /**
     * Creates a new {@link UnitOfWorkTimeoutInterceptor} for the given {@code componentName} with the given
     * {@code timeout}, {@code warningThreshold} and {@code warningInterval}. The warnings and timeout will be scheduled
     * on the {@link AxonTaskJanitor#INSTANCE}. If you want to use a different {@link ScheduledExecutorService}, use the
     * other {@link #UnitOfWorkTimeoutInterceptor(String, int, int, int, ScheduledExecutorService)}.
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
        this(componentName, timeout, warningThreshold, warningInterval, AxonTaskJanitor.INSTANCE);
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
     */
    public UnitOfWorkTimeoutInterceptor(String componentName,
                                        int timeout,
                                        int warningThreshold,
                                        int warningInterval,
                                        ScheduledExecutorService executorService
    ) {
        this.componentName = componentName;
        this.timeout = timeout;
        this.warningThreshold = warningThreshold;
        this.warningInterval = warningInterval;
        this.executorService = executorService;
    }

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends Message<?>> unitOfWork,
                         @Nonnull InterceptorChain interceptorChain) throws Exception {
        if (!unitOfWork.resources().containsKey("_transactionTimeLimit")) {
            AxonTimeLimitedTask taskTimeout = new AxonTimeLimitedTask(
                    "UnitOfWork of " + componentName,
                    timeout,
                    warningThreshold,
                    warningInterval
            );
            taskTimeout.start();
            unitOfWork.afterCommit(u -> taskTimeout.complete());
            unitOfWork.onRollback(u -> taskTimeout.complete());
        }

        return interceptorChain.proceed();
    }
}
