/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.messaging.GenericResultMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ResultMessage;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.axonframework.messaging.GenericResultMessage.asResultMessage;

/**
 * Unit of Work implementation that is able to process a batch of Messages instead of just a single Message.
 *
 * @param <T> The type of message handled by this Unit of Work
 * @author Rene de Waele
 * @author Allard Buijze
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
    public <R> ResultMessage<R> executeWithResult(Callable<R> task, RollbackConfiguration rollbackConfiguration) {
        if (phase() == Phase.NOT_STARTED) {
            start();
        }
        Assert.state(phase() == Phase.STARTED,
                     () -> String.format("The UnitOfWork has an incompatible phase: %s", phase()));
        R result = null;
        ResultMessage<R> resultMessage = asResultMessage(result);
        Throwable cause = null;
        for (MessageProcessingContext<T> processingContext : processingContexts) {
            this.processingContext = processingContext;
            try {
                result = task.call();
                if (result instanceof ResultMessage) {
                    resultMessage = (ResultMessage<R>) result;
                } else if(result instanceof Message) {
                    resultMessage = new GenericResultMessage<>(result, ((Message) result).getMetaData());
                } else {
                    resultMessage = new GenericResultMessage<>(result);
                }
            } catch (Error | Exception e) {
                if (rollbackConfiguration.rollBackOn(e)) {
                    rollback(e);
                    return asResultMessage(e);
                }
                setExecutionResult(new ExecutionResult(asResultMessage(e)));
                if (cause != null) {
                    cause.addSuppressed(e);
                } else {
                    cause = e;
                }
                resultMessage = asResultMessage(cause);
                continue;
            }
            setExecutionResult(new ExecutionResult(resultMessage));
        }
        try {
            commit();
        } catch (Exception e) {
            resultMessage = asResultMessage(e);
        }
        return resultMessage;
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
    protected void setExecutionResult(ExecutionResult executionResult) {
        processingContext.setExecutionResult(executionResult);
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
        processingContexts.forEach(context -> context
                .setExecutionResult(new ExecutionResult(new GenericResultMessage<>(cause))));
    }

    @Override
    protected void addHandler(Phase phase, Consumer<UnitOfWork<T>> handler) {
        processingContext.addHandler(phase, handler);
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

    /**
     * Checks if the message being processed now is the last of the batch being processed in this unit of work.
     *
     * @return {@code true} if the message is the last of this batch, {@code false} otherwise
     */
    public boolean isLastMessage() {
        return isLastMessage(getMessage());
    }

    /**
     * Checks if the given {@code message} is the first of the batch being processed in this unit of work.
     *
     * @param message the message to check
     * @return {@code true} if the message is the first of this batch, {@code false} otherwise
     */
    public boolean isFirstMessage(Message<?> message) {
        return processingContexts.get(0).getMessage().equals(message);
    }

    /**
     * Checks if the message being processed now is the first of the batch being processed in this unit of work.
     *
     * @return {@code true} if the message is the first of this batch, {@code false} otherwise
     */

    public boolean isFirstMessage() {
        return isFirstMessage(getMessage());
    }

}
