/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.commandhandling;

/**
 * Interface describing a callback that is invoked when command handler execution has finished. Depending of the outcome
 * of the execution, either the {@link #onSuccess(Object, CommandContext)} or the {@link #onSuccess(Object,
 * CommandContext)} is called.
 *
 * @author Allard Buijze
 * @param <C> the type of command sent
 * @param <R> the type of result of the command handling
 * @since 0.6
 */
public interface CommandCallback<C, R> {

    /**
     * Invoked when command handling execution was successful. The given <code>context</code> may contain values added
     * by the interceptors acting on the incoming command.
     *
     * @param result  The result of the command handling execution, if any.
     * @param context The context of command handling
     */
    void onSuccess(R result, CommandContext<C> context);

    /**
     * Invoked when command handling execution resulted in an error.  The given <code>context</code> may contain values
     * added by the interceptors acting on the incoming command.
     *
     * @param cause   The exception raised during command handling
     * @param context The context of command handling
     */
    void onFailure(Throwable cause, CommandContext<C> context);

}
