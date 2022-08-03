/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.disruptor.commandhandling;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.AbstractUnitOfWork;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ExecutionResult;
import org.axonframework.messaging.unitofwork.MessageProcessingContext;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static org.axonframework.messaging.GenericResultMessage.asResultMessage;

/**
 * Specialized UnitOfWork instance for the {@link DisruptorCommandBus}. It expects the executing command message to
 * target a single aggregate instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class DisruptorUnitOfWork<T extends Message<?>> extends AbstractUnitOfWork<T> {

    private MessageProcessingContext<T> processingContext;

    /**
     * Resets the state of this Unit of Work, by setting its phase to {@code NOT_STARTED}, replacing the message of this
     * Unit of Work with given {@code message}, and clearing its collection of registered handlers.
     *
     * @param message the new Message that is about to be processed.
     */
    public void reset(T message) {
        if (processingContext == null) {
            processingContext = new MessageProcessingContext<>(message);
        } else {
            processingContext.reset(message);
        }
        setPhase(Phase.NOT_STARTED);
        resources().clear();
        correlationDataProviders().clear();
    }

    /**
     * Pause this Unit of Work by unregistering it with the {@link CurrentUnitOfWork}. This will detach it from the
     * current thread.
     */
    public void pause() {
        CurrentUnitOfWork.clear(this);
    }

    /**
     * Resume a paused Unit of Work by registering it with the {@link CurrentUnitOfWork}. This will attach it to the
     * current thread again.
     */
    public void resume() {
        CurrentUnitOfWork.set(this);
    }

    @Override
    public Optional<UnitOfWork<?>> parent() {
        return Optional.empty();
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
    protected void notifyHandlers(Phase phase) {
        processingContext.notifyHandlers(this, phase);
    }

    @Override
    protected void addHandler(Phase phase, Consumer<UnitOfWork<T>> handler) {
        processingContext.addHandler(phase, handler);
    }

    @Override
    protected void setExecutionResult(ExecutionResult executionResult) {
        processingContext.setExecutionResult(executionResult);
    }

    @Override
    protected void setRollbackCause(Throwable cause) {
        setExecutionResult(new ExecutionResult(asResultMessage(cause)));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This feature is not supported by this Unit of Work.
     */
    @Override
    public <R> ResultMessage<R> executeWithResult(Callable<R> task,
                                                  @Nonnull RollbackConfiguration rollbackConfiguration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExecutionResult getExecutionResult() {
        return processingContext.getExecutionResult();
    }
}
