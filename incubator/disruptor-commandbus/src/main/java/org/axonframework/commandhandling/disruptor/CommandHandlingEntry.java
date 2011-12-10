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

import com.lmax.disruptor.AbstractEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.annotation.CommandMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * @author Allard Buijze
 */
public class CommandHandlingEntry extends AbstractEvent {

    private CommandMessage<?> command;
    private Object aggregateIdentifier;
    private InterceptorChain interceptorChain;
    private MultiThreadedUnitOfWork unitOfWork;
    private EventSourcedAggregateRoot preLoadedAggregate;
    private CommandHandler<?> commandHandler;
    private Throwable exceptionResult;
    private Object result;
    private DomainEventStream eventsToStore;
    private DomainEventStream eventsToPublish;

    public CommandMessage<?> getCommand() {
        return command;
    }

    public void setCommand(CommandMessage<?> command) {
        this.command = command;
    }

    public InterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    public void setInterceptorChain(InterceptorChain interceptorChain) {
        this.interceptorChain = interceptorChain;
    }

    public MultiThreadedUnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public void setUnitOfWork(MultiThreadedUnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    public EventSourcedAggregateRoot getPreLoadedAggregate() {
        return preLoadedAggregate;
    }

    public void setPreLoadedAggregate(EventSourcedAggregateRoot preLoadedAggregate) {
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

    public void setEventsToStore(DomainEventStream events) {
        this.eventsToStore = events;
    }

    public DomainEventStream getEventsToStore() {
        return eventsToStore;
    }

    public void setEventsToPublish(DomainEventStream events) {
        this.eventsToPublish = events;
    }

    public DomainEventStream getEventsToPublish() {
        return eventsToPublish;
    }
}
