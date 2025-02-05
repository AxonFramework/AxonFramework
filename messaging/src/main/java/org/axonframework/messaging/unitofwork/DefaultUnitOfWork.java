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

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Assert;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.ResultMessage;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static org.axonframework.messaging.GenericResultMessage.asResultMessage;

/**
 * Implementation of the UnitOfWork that processes a single message.
 *
 * @author Allard Buijze
 * @since 0.6
 */
@Deprecated // TODO #3064 Remove once old AbstractUnitOfWork is removed
public class DefaultUnitOfWork<T extends Message<?>> extends AbstractUnitOfWork<T> {

    private final MessageProcessingContext<T> processingContext;

    /**
     * Starts a new DefaultUnitOfWork instance, registering it a CurrentUnitOfWork. This methods returns the started
     * UnitOfWork instance.
     * <p>
     * Note that this Unit Of Work type is not meant to be shared among different Threads. A single DefaultUnitOfWork
     * instance should be used exclusively by the Thread that created it.
     *
     * @param message the message that will be processed in the context of the unit of work
     * @return the started UnitOfWork instance
     */
    public static <T extends Message<?>> DefaultUnitOfWork<T> startAndGet(T message) {
        DefaultUnitOfWork<T> uow = new DefaultUnitOfWork<>(message);
        uow.start();
        return uow;
    }

    /**
     * Initializes a Unit of Work (without starting it).
     *
     * @param message the message that will be processed in the context of the unit of work
     */
    public DefaultUnitOfWork(T message) {
        processingContext = new MessageProcessingContext<>(message);
    }

    @Override
    public <R> ResultMessage<R> executeWithResult(Callable<R> task,
                                                  @Nonnull RollbackConfiguration rollbackConfiguration) {
        if (phase() == Phase.NOT_STARTED) {
            start();
        }
        Assert.state(phase() == Phase.STARTED,
                     () -> String.format("The UnitOfWork has an incompatible phase: %s", phase()));
        R result;
        ResultMessage<R> resultMessage;
        try {
            //noinspection DuplicatedCode
            result = task.call();
            if (result instanceof ResultMessage) {
                //noinspection unchecked
                resultMessage = (ResultMessage<R>) result;
            } else if (result instanceof Message) {
                resultMessage = new GenericResultMessage<>(((Message<?>) result).type(),
                                                           result,
                                                           ((Message<?>) result).getMetaData());
            } else {
                resultMessage = new GenericResultMessage<>(new MessageType(ObjectUtils.nullSafeTypeOf(result)), result);
            }
        } catch (Error | Exception e) {
            resultMessage = asResultMessage(e);
            if (rollbackConfiguration.rollBackOn(e)) {
                rollback(e);
                return resultMessage;
            }
        }
        setExecutionResult(new ExecutionResult(resultMessage));
        try {
            commit();
        } catch (Exception e) {
            resultMessage = asResultMessage(e);
        }
        return resultMessage;
    }

    @Override
    protected void setRollbackCause(Throwable cause) {
        MessageType type = new MessageType(ObjectUtils.nullSafeTypeOf(cause));
        setExecutionResult(new ExecutionResult(new GenericResultMessage<>(type, cause)));
    }

    @Override
    protected void notifyHandlers(Phase phase) {
        processingContext.notifyHandlers(this, phase);
    }

    @Override
    protected void addHandler(Phase phase, Consumer<UnitOfWork<T>> handler) {
        Assert.state(!phase.isBefore(phase()), () -> "Cannot register a listener for phase: " + phase
                + " because the Unit of Work is already in a later phase: " + phase());
        processingContext.addHandler(phase, handler);
    }

    @Override
    public T getMessage() {
        return processingContext.getMessage();
    }

    @Override
    public UnitOfWork<T> transformMessage(Function<T, ? extends Message<?>> transformOperator) {
        processingContext.transformMessage(transformOperator);
        return this;
    }

    @Override
    public ExecutionResult getExecutionResult() {
        return processingContext.getExecutionResult();
    }

    @Override
    protected void setExecutionResult(ExecutionResult executionResult) {
        processingContext.setExecutionResult(executionResult);
    }
}
