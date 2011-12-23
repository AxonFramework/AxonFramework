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

import com.lmax.disruptor.EventHandler;
import org.axonframework.eventsourcing.EventSourcedAggregateRoot;

/**
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandHandlerInvoker<T extends EventSourcedAggregateRoot>
        implements EventHandler<CommandHandlingEntry<T>> {

    private final ThreadLocal<T> preLoadedAggregate = new ThreadLocal<T>();

    @Override
    public void onEvent(CommandHandlingEntry<T> entry, long sequence, boolean endOfBatch) throws Exception {
        DisruptorUnitOfWork unitOfWork = entry.getUnitOfWork();
        unitOfWork.start();
        unitOfWork.registerAggregate(entry.getPreLoadedAggregate(), null, null);

        preLoadedAggregate.set(entry.getPreLoadedAggregate());
        try {
            Object result = entry.getInterceptorChain().proceed(entry.getCommand());
            entry.setResult(result);
        } catch (Throwable throwable) {
            entry.setExceptionResult(throwable);
        }
        unitOfWork.commit();
        preLoadedAggregate.remove();
    }

    /**
     * Returns the aggregate that has been preloaded for execution by this invoker. This is the aggregate expected to
     * be loaded by the command handler.
     *
     * @return the pre-loaded aggregate
     */
    public T getPreLoadedAggregate() {
        return preLoadedAggregate.get();
    }
}
