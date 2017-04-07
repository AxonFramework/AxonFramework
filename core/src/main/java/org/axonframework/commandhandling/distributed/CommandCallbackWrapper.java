/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;

/**
 * Wrapper for a Command callback. This is used in a CommandCallbackRepository
 *
 * @param <A> The type of the session identifier
 * @param <C> The type of the command
 * @param <R> The type of the expected result
 * @author Koen Lavooij
 */
public class CommandCallbackWrapper<A, C, R> implements CommandCallback<C, R> {
    private final CommandCallback<? super C, R> wrapped;
    private final A sessionId;
    private final CommandMessage<C> message;

    /**
     * Initializes a {@link CommandCallbackWrapper} which wraps the original callback and holds on to the
     * command {@code message} and {@code channelId} of the channel on which the message is sent.
     *
     * @param channelId used to identify the channel used to send the message
     * @param message   the command message that was sent
     * @param callback  the command callback to notify when the command result is received
     */
    public CommandCallbackWrapper(A channelId, CommandMessage<C> message, CommandCallback<? super C, R> callback) {
        this.wrapped = callback;
        this.sessionId = channelId;
        this.message = message;
    }

    /**
     * Returns the command message that was sent.
     *
     * @return the sent message
     */
    public CommandMessage<C> getMessage() {
        return message;
    }

    /**
     * Returns the identifier of the channel over which the command message was sent.
     *
     * @return the identifier of the channel over which the command message was sent
     */
    public A getChannelIdentifier() {
        return sessionId;
    }

    /**
     * Invokes {@link CommandCallback#onFailure(CommandMessage, Throwable)} with given exception on the wrapped
     * callback.
     *
     * @param e cause for the failure
     */
    public void fail(Throwable e) {
        onFailure(getMessage(), e);
    }

    /**
     * Invokes {@link CommandCallback#onSuccess(CommandMessage, Object)} with given {@code result} on the wrapped
     * callback.
     *
     * @param result the result of the command
     */
    public void success(R result) {
        onSuccess(getMessage(), result);
    }

    @Override
    public void onSuccess(CommandMessage<? extends C> message, R result) {
        wrapped.onSuccess(message, result);
    }

    @Override
    public void onFailure(CommandMessage<? extends C> message, Throwable cause) {
        wrapped.onFailure(message, cause);
    }
}
