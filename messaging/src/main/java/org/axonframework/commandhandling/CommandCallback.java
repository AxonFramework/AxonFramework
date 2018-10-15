/*
 * Copyright (c) 2010-2018. Axon Framework
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
    void onResult(CommandMessage<? extends C> commandMessage, CommandResultMessage<? extends R> commandResultMessage);
}
