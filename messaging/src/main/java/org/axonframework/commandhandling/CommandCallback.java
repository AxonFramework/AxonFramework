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
 * Interface describing a callback that is invoked when command handler execution has finished.
 *
 * @param <R> the type of result of the command handling
 * @param <C> the type of payload of the command
 * @author Allard Buijze
 * @since 0.6
 */
@FunctionalInterface
public interface CommandCallback<C, R> {

    /**
     * Invoked when command handling execution is completed.
     *
     * @param commandMessage       the {@link CommandMessage} that was dispatched
     * @param commandResultMessage the {@link CommandResultMessage} of the command handling execution
     */
    void onResult(@Nonnull CommandMessage<? extends C> commandMessage,
                  @Nonnull CommandResultMessage<? extends R> commandResultMessage);

    /**
     * Wraps the command callback with another using a {@link WrappedCommandCallback}. If provided with a null
     * callback this method will not wrap it, keeping the original callback instead.
     * <p>
     * In effect, the given callback will be executed first, and then the original will be executed second. You can wrap
     * callback as many times as you'd like.
     *
     * @param wrappingCallback The command callback that should wrap the current instance
     * @return The {@link WrappedCommandCallback} representing the execution of both callbacks
     * @since 4.6.0
     * @author Mitchell Herrijgers
     */
    default CommandCallback<C, R> wrap(CommandCallback<C, R> wrappingCallback) {
        if (wrappingCallback == null) {
            return this;
        }
        return new WrappedCommandCallback<>(wrappingCallback, this);
    }
}
