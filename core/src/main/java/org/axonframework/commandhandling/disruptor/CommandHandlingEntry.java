/*
 * Copyright (c) 2010-2014. Axon Framework
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
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.DefaultInterceptorChain;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.unitofwork.UnitOfWork;

import java.util.List;

/**
 * DataHolder for the DisruptorCommandBus. The CommandHandlingEntry maintains all information required for or produced
 * by the command handling process.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlingEntry {

    private final CommandHandler<Object> repeatingCommandHandler;
    private CommandMessage<?> command;
    private InterceptorChain invocationInterceptorChain;
    private InterceptorChain publisherInterceptorChain;
    private DisruptorUnitOfWork unitOfWork;
    private Throwable exceptionResult;
    private Object result;
    private int publisherSegmentId;
    private BlacklistDetectingCallback callback;
    // for recovery of corrupt aggregates
    private boolean isRecoverEntry;
    private String aggregateIdentifier;
    private int invokerSegmentId;
    private int serializerSegmentId;
    private final boolean transactional;

    /**
     * Initializes the CommandHandlingEntry
     *
     * @param transactional Whether this entry contains transactional Unit of Work
     */
    public CommandHandlingEntry(boolean transactional) {
        this.transactional = transactional;
        repeatingCommandHandler = new RepeatingCommandHandler();
    }

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
     * Returns the InterceptorChain for the publication process registered with this entry, or <code>null</code> if
     * none
     * is available.
     *
     * @return the InterceptorChain for the publication process registered with this entry
     */
    public InterceptorChain getPublisherInterceptorChain() {
        return publisherInterceptorChain;
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
     * Returns the result of the command's execution, or <code>null</code> if the command is not yet executed or
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
     * Returns the Segment ID that identifies the serializer thread to process this entry
     *
     * @return the Segment ID that identifies the serializer thread to process this entry
     */
    public int getSerializerSegmentId() {
        return serializerSegmentId;
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
     * @param newCommand             The new command the entry is used for
     * @param newCommandHandler      The Command Handler responsible for handling <code>newCommand</code>
     * @param newInvokerSegmentId    The SegmentID of the invoker that should process this entry
     * @param newPublisherSegmentId  The SegmentID of the publisher that should process this entry
     * @param newSerializerSegmentId The SegmentID of the serializer that should process this entry
     * @param newCallback            The callback to report the result of command execution to
     * @param invokerInterceptors    The interceptors to invoke during the command handler invocation phase
     * @param publisherInterceptors  The interceptors to invoke during the publication phase
     */
    public void reset(CommandMessage<?> newCommand, CommandHandler newCommandHandler, // NOSONAR - Not important
                      int newInvokerSegmentId, int newPublisherSegmentId, int newSerializerSegmentId,
                      BlacklistDetectingCallback newCallback, List<CommandHandlerInterceptor> invokerInterceptors,
                      List<CommandHandlerInterceptor> publisherInterceptors) {
        this.command = newCommand;
        this.invokerSegmentId = newInvokerSegmentId;
        this.publisherSegmentId = newPublisherSegmentId;
        this.serializerSegmentId = newSerializerSegmentId;
        this.callback = newCallback;
        this.isRecoverEntry = false;
        this.aggregateIdentifier = null;
        this.result = null;
        this.exceptionResult = null;
        this.unitOfWork = new DisruptorUnitOfWork(transactional);
        this.invocationInterceptorChain = new DefaultInterceptorChain(newCommand,
                                                                      unitOfWork,
                                                                      newCommandHandler,
                                                                      invokerInterceptors);
        this.publisherInterceptorChain = new DefaultInterceptorChain(newCommand,
                                                                     unitOfWork,
                                                                     repeatingCommandHandler,
                                                                     publisherInterceptors);
    }

    /**
     * Resets this entry, preparing it for use as a recovery entry.
     *
     * @param newAggregateIdentifier The identifier of the aggregate to recover
     */
    public void resetAsRecoverEntry(String newAggregateIdentifier) {
        this.isRecoverEntry = true;
        this.aggregateIdentifier = newAggregateIdentifier;
        this.command = null;
        this.callback = null;
        result = null;
        exceptionResult = null;
        invocationInterceptorChain = null;
        unitOfWork = null;
        invokerSegmentId = -1;
        serializerSegmentId = -1;
    }

    /**
     * Factory class for CommandHandlingEntry instances.
     */
    public static class Factory implements EventFactory<CommandHandlingEntry> {

        private final boolean transactional;

        /**
         * Initialize the factory with given <code>transactional</code> flag
         *
         * @param transactional whether the entries contain transactional Units of Work
         */
        public Factory(boolean transactional) {
            this.transactional = transactional;
        }

        @Override
        public CommandHandlingEntry newInstance() {
            return new CommandHandlingEntry(transactional);
        }
    }

    private class RepeatingCommandHandler implements CommandHandler<Object> {

        @Override
        public Object handle(CommandMessage<Object> commandMessage, UnitOfWork uow) throws Throwable {
            if (exceptionResult != null) {
                throw exceptionResult;
            }
            return result;
        }
    }
}
