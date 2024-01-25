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

package org.axonframework.disruptor.commandhandling;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * DataHolder for the DisruptorCommandBus. The CommandHandlingEntry maintains all information required for or produced
 * by the command handling process.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlingEntry extends DisruptorUnitOfWork<CommandMessage<?>> {

    private final MessageHandler<CommandMessage<?>, CommandResultMessage<?>> repeatingCommandHandler;
    private InterceptorChain invocationInterceptorChain;
    private InterceptorChain publisherInterceptorChain;
    private CommandResultMessage<?> result;
    private int publisherSegmentId;
    private BlacklistDetectingCallback<?, ?> callback;
    // for recovery of corrupt aggregates
    private boolean isRecoverEntry;
    private String aggregateIdentifier;
    private int invokerSegmentId;

    /**
     * Initializes the CommandHandlingEntry
     */
    public CommandHandlingEntry() {
        repeatingCommandHandler = new RepeatingCommandHandler();
    }

    /**
     * Returns the InterceptorChain for the invocation process registered with this entry, or {@code null} if none is
     * available.
     *
     * @return the InterceptorChain for the invocation process registered with this entry
     */
    public InterceptorChain getInvocationInterceptorChain() {
        return invocationInterceptorChain;
    }

    /**
     * Returns the InterceptorChain for the publication process registered with this entry, or {@code null} if none is
     * available.
     *
     * @return the InterceptorChain for the publication process registered with this entry
     */
    public InterceptorChain getPublisherInterceptorChain() {
        return publisherInterceptorChain;
    }

    /**
     * Registers the result of the command's execution, if successful.
     *
     * @param result the result of the command's execution, if successful
     */
    public void setResult(CommandResultMessage<?> result) {
        this.result = result;
    }

    /**
     * Returns the result of the command's execution, or {@code null} if the command is not yet executed or resulted in
     * an exception.
     *
     * @return the result of the command's execution, if any
     */
    public CommandResultMessage<?> getResult() {
        return result;
    }

    /**
     * Returns the CommandCallback instance for the executed command.
     *
     * @return the CommandCallback instance for the executed command
     */
    public BlacklistDetectingCallback<?, ?> getCallback() {
        return callback;
    }

    /**
     * Indicates whether this entry is a recovery entry. When {@code true}, this entry does not contain any command
     * handling information.
     *
     * @return {@code true} if this entry represents a recovery request, otherwise {@code false}.
     */
    public boolean isRecoverEntry() {
        return isRecoverEntry;
    }

    /**
     * Returns the identifier of the aggregate to recover. Returns {@code null} when {@link #isRecoverEntry()} returns
     * {@code false}.
     *
     * @return the identifier of the aggregate to recover
     */
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Returns the Identifier of the invoker that is chosen to handle this entry.
     *
     * @return the Identifier of the invoker that is chosen to handle this entry
     */
    public int getInvokerId() {
        return invokerSegmentId;
    }

    /**
     * Returns the Identifier of the publisher that is chosen to handle this entry.
     *
     * @return the Identifier of the publisher that is chosen to handle this entry
     */
    public int getPublisherId() {
        return publisherSegmentId;
    }

    /**
     * Resets this entry, preparing it for use for another command.
     *
     * @param newCommand            The new command the entry is used for
     * @param newCommandHandler     The Command Handler responsible for handling {@code newCommand}
     * @param newInvokerSegmentId   The SegmentID of the invoker that should process this entry
     * @param newPublisherSegmentId The SegmentID of the publisher that should process this entry
     * @param newCallback           The callback to report the result of command execution to
     * @param invokerInterceptors   The interceptors to invoke during the command handler invocation phase
     * @param publisherInterceptors The interceptors to invoke during the publication phase
     */
    public void reset(CommandMessage<?> newCommand,
                      MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> newCommandHandler,// NOSONAR - Not important
                      int newInvokerSegmentId,
                      int newPublisherSegmentId,
                      BlacklistDetectingCallback<?, ?> newCallback,
                      List<MessageHandlerInterceptor<? super CommandMessage<?>>> invokerInterceptors,
                      List<MessageHandlerInterceptor<? super CommandMessage<?>>> publisherInterceptors) {
        this.invokerSegmentId = newInvokerSegmentId;
        this.publisherSegmentId = newPublisherSegmentId;
        this.callback = newCallback;
        this.isRecoverEntry = false;
        this.result = null;
        this.aggregateIdentifier = null;
        this.invocationInterceptorChain = new DefaultInterceptorChain<>(
                this,
                invokerInterceptors, newCommandHandler
        );
        this.publisherInterceptorChain = new DefaultInterceptorChain<>(
                this,
                publisherInterceptors, repeatingCommandHandler
        );
        reset(newCommand);
    }

    /**
     * Resets this entry, preparing it for use as a recovery entry.
     *
     * @param newAggregateIdentifier The identifier of the aggregate to recover
     */
    public void resetAsRecoverEntry(String newAggregateIdentifier) {
        this.isRecoverEntry = true;
        this.callback = null;
        result = null;
        invocationInterceptorChain = null;
        invokerSegmentId = -1;
        publisherSegmentId = -1;
        this.aggregateIdentifier = newAggregateIdentifier;
        reset(null);
    }

    /**
     * Resets this entry, preparing it to run given {@code callable} from within the {@code
     * invocationInterceptorChain}.
     *
     * @param callable              a {@link Callable} which performs a task in the {@code invocationInterceptorChain},
     *                              for example publishing a scheduled {@link org.axonframework.deadline.DeadlineMessage}
     * @param newInvokerSegmentId   The SegmentId of the invoker that should process this entry
     * @param newPublisherSegmentId The SegmentId of the publisher that should process this entry
     * @param newCallback           The callback to report the result of command execution to
     */
    public void resetAsCallable(Callable<Object> callable,
                                int newInvokerSegmentId,
                                int newPublisherSegmentId,
                                BlacklistDetectingCallback<Object, Object> newCallback) {
        this.isRecoverEntry = false;
        this.invokerSegmentId = newInvokerSegmentId;
        this.publisherSegmentId = newPublisherSegmentId;
        this.callback = newCallback;
        result = null;
        aggregateIdentifier = null;
        invocationInterceptorChain = callable::call;
        publisherInterceptorChain = () -> repeatingCommandHandler.handleSync(null);
        reset(null);
    }

    /**
     * Registers the identifier of the aggregate that will process the next command.
     *
     * @param aggregateIdentifier identifier of the aggregate that will handle the command
     */
    public void registerAggregateIdentifier(String aggregateIdentifier) {
        if (this.aggregateIdentifier != null && !this.aggregateIdentifier.equals(aggregateIdentifier)) {
            throw new IllegalStateException("Cannot load multiple aggregates in the same unit of work when using" +
                                                    "DisruptorCommandBus! Already loaded " + this.aggregateIdentifier +
                                                    ", attempted to load " + aggregateIdentifier);
        }
        this.aggregateIdentifier = aggregateIdentifier;
    }

    private class RepeatingCommandHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        @Override
        public Object handleSync(CommandMessage<?> message) throws Exception {
            return result;
        }
    }
}
