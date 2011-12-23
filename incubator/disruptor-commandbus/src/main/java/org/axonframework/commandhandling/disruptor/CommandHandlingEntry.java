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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * @author Allard Buijze
 */
public class CommandHandlingEntry<T extends EventSourcedAggregateRoot> {

    private CommandMessage<?> command;
    private Object aggregateIdentifier;
    private InterceptorChain interceptorChain;
    private DisruptorUnitOfWork unitOfWork;
    private T preLoadedAggregate;
    private CommandHandler<?> commandHandler;
    private Throwable exceptionResult;
    private Object result;

    public CommandMessage<?> getCommand() {
        return command;
    }

    public InterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    public void setInterceptorChain(InterceptorChain interceptorChain) {
        this.interceptorChain = interceptorChain;
    }

    public DisruptorUnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public void setUnitOfWork(DisruptorUnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    public T getPreLoadedAggregate() {
        return preLoadedAggregate;
    }

    public void setPreLoadedAggregate(T preLoadedAggregate) {
        this.preLoadedAggregate = preLoadedAggregate;
    }

    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public void setCommandHandler(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public void setAggregateIdentifier(Object aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    public void setExceptionResult(Throwable exceptionResult) {
        this.exceptionResult = exceptionResult;
    }

    public Throwable getExceptionResult() {
        return exceptionResult;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Object getResult() {
        return result;
    }

    public void reset(CommandMessage<?> command, Object aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.command = command;
        result = null;
        exceptionResult = null;
        commandHandler = null;
        interceptorChain = null;
        unitOfWork = null;
        preLoadedAggregate = null;
    }
}
