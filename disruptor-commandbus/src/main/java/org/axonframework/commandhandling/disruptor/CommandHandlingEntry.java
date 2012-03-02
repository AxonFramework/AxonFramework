/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.disruptor;

import com.lmax.disruptor.EventFactory;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * DataHolder for the DisruptorCommandBus. The CommandHandlingEntry maintains all information required for or produced
 * by the command handling process.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlingEntry<T extends EventSourcedAggregateRoot> {

    private CommandMessage<?> command;
    private InterceptorChain invocationInterceptorChain;
    private InterceptorChain publisherInterceptorChain;
    private DisruptorUnitOfWork unitOfWork;
    private T preLoadedAggregate;
    private CommandHandler<?> commandHandler;
    private Throwable exceptionResult;
    private Object result;
    private BlacklistDetectingCallback callback;

    // for recovery of corrupt aggregates
    private boolean isRecoverEntry;
    private Object aggregateIdentifier;

    /**
     * Returns the CommandMessage to be executed.
     *
     * @return the CommandMessage to be executed
     */
    public CommandMessage<?> getCommand() {
        return command;
    }

    /**
     * Returns the InterceptorChain for the invocation process registered with this entry, or <code>null</code> if none
     * is available.
     *
     * @return the InterceptorChain for the invocation process registered with this entry
     */
    public InterceptorChain getInvocationInterceptorChain() {
        return invocationInterceptorChain;
    }

    /**
     * Registers the InterceptorChain to use for the command execution.
     *
     * @param interceptorChain the InterceptorChain to use for the command execution
     */
    public void setInvocationInterceptorChain(InterceptorChain interceptorChain) {
        this.invocationInterceptorChain = interceptorChain;
    }

    /**
     * Returns the InterceptorChain for the publication process registered with this entry, or <code>null</code> if none
     * is available.
     *
     * @return the InterceptorChain for the publication process registered with this entry
     */
    public InterceptorChain getPublisherInterceptorChain() {
        return publisherInterceptorChain;
    }

    /**
     * Registers the InterceptorChain to use for the event publication.
     *
     * @param interceptorChain the InterceptorChain to use for the command execution
     */
    public void setPublisherInterceptorChain(InterceptorChain interceptorChain) {
        this.publisherInterceptorChain = interceptorChain;
    }

    /**
     * Returns the UnitOfWork for the command execution.
     *
     * @return the UnitOfWork for the command execution
     */
    public DisruptorUnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    /**
     * Registers the UnitOfWork to use for the command execution.
     *
     * @param unitOfWork the UnitOfWork to use for the command execution
     */
    public void setUnitOfWork(DisruptorUnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    /**
     * Returns the Aggregate that has been pre-loaded for the command execution.
     *
     * @return the Aggregate that has been pre-loaded for the command execution
     */
    public T getPreLoadedAggregate() {
        return preLoadedAggregate;
    }

    /**
     * Registers the Aggregate that has been pre-loaded for the command execution.
     *
     * @param preLoadedAggregate Aggregate the that has been pre-loaded for the command execution
     */
    public void setPreLoadedAggregate(T preLoadedAggregate) {
        this.preLoadedAggregate = preLoadedAggregate;
    }

    /**
     * Returns the Handler to execute the incoming command.
     *
     * @return the Handler to execute the incoming command
     */
    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    /**
     * Registers the Handler to execute the incoming command.
     *
     * @param commandHandler the Handler to execute the incoming command
     */
    public void setCommandHandler(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    /**
     * Registers the exception that occurred while processing the incoming command.
     *
     * @param exceptionResult the exception that occurred while processing the incoming command
     */
    public void setExceptionResult(Throwable exceptionResult) {
        this.exceptionResult = exceptionResult;
    }

    /**
     * Returns the exception that occurred while processing the incoming command, or <code>null</code> if
     * processing did not result in an exception or if execution is not yet finished.
     *
     * @return the exception that occurred while processing the incoming command, if any.
     */
    public Throwable getExceptionResult() {
        return exceptionResult;
    }

    /**
     * Registers the result of the command's execution, if successful.
     *
     * @param result the result of the command's execution, if successful
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * Returns the result of the command's execution, or <code>null</code> if the commmand is not yet executed or
     * resulted in an exception.
     *
     * @return the result of the command's execution, if any
     */
    public Object getResult() {
        return result;
    }

    /**
     * Returns the CommandCallback instance for the executed command.
     *
     * @return the CommandCallback instance for the executed command
     */
    public BlacklistDetectingCallback getCallback() {
        return callback;
    }

    /**
     * Indicates whether this entry is a recovery entry. When <code>true</code>, this entry does not contain any
     * command
     * handling information.
     *
     * @return <code>true</code> if this entry represents a recovery request, otherwise <code>false</code>.
     */
    public boolean isRecoverEntry() {
        return isRecoverEntry;
    }

    /**
     * Returns the identifier of the aggregate to recover. Returns <code>null</code> when {@link #isRecoverEntry()}
     * returns <code>false</code>.
     *
     * @return the identifier of the aggregate to recover
     */
    public Object getRecoveringAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Resets this entry, preparing it for use for another command.
     *
     * @param command  The new command the entry is used for
     * @param callback The callback to report the result of command execution to
     */
    public void reset(CommandMessage<?> command, BlacklistDetectingCallback callback) {
        this.command = command;
        this.callback = callback;
        this.isRecoverEntry = false;
        this.aggregateIdentifier = null;
        result = null;
        exceptionResult = null;
        commandHandler = null;
        invocationInterceptorChain = null;
        unitOfWork = null;
        preLoadedAggregate = null;
    }

    /**
     * Resets this entry, preparing it for use as a recovery entry.
     *
     * @param aggregateIdentifier The identifier of the aggregate to recover
     */
    public void resetAsRecoverEntry(Object aggregateIdentifier) {
        this.isRecoverEntry = true;
        this.aggregateIdentifier = aggregateIdentifier;
        this.command = null;
        this.callback = null;
        result = null;
        exceptionResult = null;
        commandHandler = null;
        invocationInterceptorChain = null;
        unitOfWork = null;
        preLoadedAggregate = null;
    }

    /**
     * Factory class for CommandHandlingEntry instances.
     *
     * @param <T> The type of aggregate the command bus processes commands for
     */
    public static class Factory<T extends EventSourcedAggregateRoot> implements EventFactory<CommandHandlingEntry<T>> {

        @Override
        public CommandHandlingEntry<T> newInstance() {
            return new CommandHandlingEntry<T>();
        }
    }
}
