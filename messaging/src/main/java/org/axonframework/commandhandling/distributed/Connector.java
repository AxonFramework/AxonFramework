/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Interface for mechanisms that Connect one or more Command Bus instances to each other, whether remotely and connected
 * via the network, or locally.
 * <p>
 * Unlike the CommandBus, the connector uses callbacks to allow the result of a dispatch to be returned before any
 * post-processing is done.
 */
public interface Connector {

    CompletableFuture<? extends CommandResultMessage<?>> dispatch(CommandMessage<?> command,
                                                                  ProcessingContext processingContext);

    void subscribe(String commandName, int loadFactor);

    boolean unsubscribe(String commandName);

    void onIncomingCommand(BiConsumer<CommandMessage<?>, ResultCallback> handler);

    // using a callback allows us to ack messages before requesting more
    // when returning a CompletableFuture, this isn't possible, as the request
    // would always happen before any object is returned from the onIncomingCommand function
    interface ResultCallback {

        void success(CommandResultMessage<?> resultMessage);

        void error(Throwable cause);
    }
}
