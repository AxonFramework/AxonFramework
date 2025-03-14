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

package org.axonframework.commandhandling.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.InterceptingCommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.Connector;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.commandhandling.tracing.CommandBusSpanFactory;
import org.axonframework.commandhandling.tracing.TracingCommandBus;
import org.axonframework.common.DirectExecutor;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.ProcessingLifecycleHandlerRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

public class CommandBusBuilder /* implements ConfigurerModule or better ComponentConfigurer/ComponentProvider */ {

    private final Function<CommandBusBuilder, CommandBus> creator;
    private List<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars = new ArrayList<>();
    private Executor executor = DirectExecutor.INSTANCE;
    private List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors = new ArrayList<>();
    private List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors = new ArrayList<>();
    private List<Function<CommandBus, CommandBus>> wrappers = new ArrayList<>();

    private CommandBusBuilder(Function<CommandBusBuilder, CommandBus> creator) {
        this.creator = creator;
    }

    public static CommandBusBuilder forSimpleCommandBus() {
        return new CommandBusBuilder(builder -> new SimpleCommandBus(builder.executor, builder.processingLifecycleHandlerRegistrars));
    }

    // TODO - Parameter should be `Function<Configuration, CommandBus> builder`
    public static CommandBusBuilder forCustomCommandBus(Supplier<CommandBus> builder) {
        return new CommandBusBuilder(it -> builder.get());
    }

    public CommandBusBuilder withWorker(Executor worker) {
        this.executor = executor;
        return this;
    }

    public CommandBusBuilder withDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> messageDispatchInterceptor) {
        this.dispatchInterceptors.add(messageDispatchInterceptor);
        return this;
    }

    public CommandBusBuilder withHandlerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> messageHandlerInterceptor) {
        this.handlerInterceptors.add(messageHandlerInterceptor);
        return this;
    }

    public CommandBusBuilder withTracing(CommandBusSpanFactory spanFactory) {
        this.wrappers.add(c -> new TracingCommandBus(c, spanFactory));
        return this;
    }

    public CommandBusBuilder distributedVia(Connector connector) {
        this.wrappers.add(c -> new DistributedCommandBus(c, connector));
        return this;
    }

    public CommandBusBuilder withTransactions(TransactionManager transactionManager) {
        if (!processingLifecycleHandlerRegistrars.contains(transactionManager)) {
            this.processingLifecycleHandlerRegistrars.add(transactionManager);
        }
        return this;
    }

    public CommandBus build() {
        var finalCreator = creator;
        if (!dispatchInterceptors.isEmpty() || !handlerInterceptors.isEmpty()) {
            finalCreator = b -> new InterceptingCommandBus(creator.apply(b), handlerInterceptors, dispatchInterceptors);
        }
        for (Function<CommandBus, CommandBus> wrapper : wrappers) {
            var previous = finalCreator;
            finalCreator = b -> wrapper.apply(previous.apply(b));
        }
        return finalCreator.apply(this);
    }
}
