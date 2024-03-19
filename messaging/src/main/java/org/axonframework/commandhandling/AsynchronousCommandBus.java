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

package org.axonframework.commandhandling;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingLifecycleHandlerRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

/**
 * Specialization of the SimpleCommandBus that processed Commands asynchronously from the calling thread. By default,
 * the AsynchronousCommandBus uses a Cached Thread Pool (see
 * {@link java.util.concurrent.Executors#newCachedThreadPool()}). It will reuse threads while possible, and shut them
 * down after 60 seconds of inactivity.
 * <p/>
 * Each Command is dispatched in a separate task, which is processed by the Executor.
 *
 * @author Allard Buijze
 * @since 1.3.4
 */
public class AsynchronousCommandBus extends SimpleCommandBus {

    private static final Logger logger = LoggerFactory.getLogger(AsynchronousCommandBus.class);
    private final Executor executor;

    public AsynchronousCommandBus(Executor executor,
                                  ProcessingLifecycleHandlerRegistrar... processingLifecycleHandlerRegistrars) {
        super(processingLifecycleHandlerRegistrars);
        this.executor = executor;
    }

    public AsynchronousCommandBus(Executor executor,
                                  Collection<ProcessingLifecycleHandlerRegistrar> processingLifecycleHandlerRegistrars) {
        super(processingLifecycleHandlerRegistrars);
        this.executor = executor;
    }

    @Override
    protected CompletableFuture<CommandResultMessage<?>> handle(CommandMessage<?> command,
                                                                MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        CompletableFuture<CommandResultMessage<?>> result = new CompletableFuture<>();
        executor.execute(() -> super.handle(command, handler).whenComplete((r, e) -> {
            if (e == null) {
                result.complete(r);
            } else {
                result.completeExceptionally(e);
            }
        }));
        return result;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("executor", executor);
        super.describeTo(descriptor);
    }
}
