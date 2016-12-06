/*
 * Copyright (c) 2010-2015. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unit of Work implementation that is able to process a batch of Messages instead of just a single Message.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class BatchingUnitOfWork<T extends Message<?>> extends AbstractUnitOfWork<T> {

    private final List<MessageProcessingContext<T>> processingContexts;
    private MessageProcessingContext<T> processingContext;

    /**
     * Initializes a BatchingUnitOfWork for processing the given batch of {@code messages}.
     *
     * @param messages batch of messages to process
     */
    @SafeVarargs
    public BatchingUnitOfWork(T... messages) {
        this(Arrays.asList(messages));
    }

    /**
     * Initializes a BatchingUnitOfWork for processing the given list of {@code messages}.
     *
     * @param messages batch of messages to process
     */
    public BatchingUnitOfWork(List<T> messages) {
        Assert.isFalse(messages.isEmpty(), () -> "The list of Messages to process is empty");
        processingContexts = messages.stream().map(MessageProcessingContext::new).collect(Collectors.toList());
        processingContext = processingContexts.get(0);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p/>
     * This implementation executes the given {@code task} for each of its messages. The return value is the
     * result of the last executed task.
     */
    @Override
    public <R> R executeWithResult(Callable<R> task, RollbackConfiguration rollbackConfiguration) throws Exception {
        if (phase() == Phase.NOT_STARTED) {
            start();
        }
        Assert.state(phase() == Phase.STARTED,
                     () -> String.format("The UnitOfWork has an incompatible phase: %s", phase()));
        R result = null;
        Exception exception = null;
        for (MessageProcessingContext<T> processingContext : processingContexts) {
            this.processingContext = processingContext;
            try {
                result = task.call();
            } catch (Exception e) {
                if (rollbackConfiguration.rollBackOn(e)) {
                    rollback(e);
                    throw e;
                }
                setExecutionResult(new ExecutionResult(e));
                if (exception != null) {
                    exception.addSuppressed(e);
                } else {
                    exception = e;
                }
                continue;
            }
            setExecutionResult(new ExecutionResult(result));
        }
        commit();
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    /**
     * Returns a Map of {@link ExecutionResult} per Message. If the Unit of Work has not been given a task
     * to execute, the ExecutionResult is {@code null} for each Message.
     *
     * @return a Map of ExecutionResult per Message processed by this Unit of Work
     */
    public Map<Message<?>, ExecutionResult> getExecutionResults() {
        return processingContexts.stream().collect(
                Collectors.toMap(MessageProcessingContext::getMessage, MessageProcessingContext::getExecutionResult));
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
    protected void notifyHandlers(Phase phase) {
        Iterator<MessageProcessingContext<T>> iterator =
                phase.isReverseCallbackOrder() ? new LinkedList<>(processingContexts).descendingIterator() :
                        processingContexts.iterator();
        iterator.forEachRemaining(context -> (processingContext = context).notifyHandlers(this, phase));
    }

    @Override
    protected void setRollbackCause(Throwable cause) {
        processingContexts.forEach(context -> context.setExecutionResult(new ExecutionResult(cause)));
    }

    @Override
    protected void addHandler(Phase phase, Consumer<UnitOfWork<T>> handler) {
        processingContext.addHandler(phase, handler);
    }

    @Override
    protected void setExecutionResult(ExecutionResult executionResult) {
        processingContext.setExecutionResult(executionResult);
    }

    /**
     * Get the batch of messages that is being processed (or has been processed) by this unit of work.
     *
     * @return the message batch
     */
    public List<? extends Message<?>> getMessages() {
        return processingContexts.stream().map(MessageProcessingContext::getMessage).collect(Collectors.toList());
    }

    /**
     * Checks if the given {@code message} is the last of the batch being processed in this unit of work.
     *
     * @param message the message to check for
     * @return {@code true} if the message is the last of this batch, {@code false} otherwise
     */
    public boolean isLastMessage(Message<?> message) {
        return processingContexts.get(processingContexts.size() - 1).getMessage().equals(message);
    }
}
