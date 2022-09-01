/*
 * Copyright (c) 2010-2022. Axon Framework
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

import javax.annotation.Nonnull;

/**
 * Represents a {@link CommandCallback} that was wrapped with another. When the {@link WrappedCommandCallback} is
 * called, it will first execute the outer callback, then execute the inner callback.
 * You can wrap a {@link CommandCallback} with another using {@link CommandCallback#wrap(CommandCallback)}.
 *
 * @param <R> the type of result of the command handling
 * @param <C> the type of payload of the command
 * @since 4.6.0
 * @author Mitchell Herrijgers
 */
public class WrappedCommandCallback<C, R> implements CommandCallback<C, R> {

    private final CommandCallback<C, R> outerCallback;
    private final CommandCallback<C, R> innerCallback;

    /**
     * Constructs a new {@link WrappedCommandCallback}, called by using {@link CommandCallback#wrap(CommandCallback)}.
     *
     * @param outerCallback The outer callback. This callback will be executed first.
     * @param innerCallback The inner callback.
     */
    protected WrappedCommandCallback(CommandCallback<C, R> outerCallback, CommandCallback<C, R> innerCallback) {
        this.outerCallback = outerCallback;
        this.innerCallback = innerCallback;
    }

    @Override
    public void onResult(@Nonnull CommandMessage<? extends C> commandMessage,
                         @Nonnull CommandResultMessage<? extends R> commandResultMessage) {
        outerCallback.onResult(commandMessage, commandResultMessage);
        innerCallback.onResult(commandMessage, commandResultMessage);
    }
}
