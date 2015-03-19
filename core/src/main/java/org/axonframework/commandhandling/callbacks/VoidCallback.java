/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.callbacks;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;

/**
 * Abstract callback that can be extended when no result is expected from the command handler execution.
 *
 * @param <C> The type of payload of the dispatched command
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class VoidCallback<C> implements CommandCallback<C, Object> {

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation merely invokes {@link #onSuccess(org.axonframework.commandhandling.CommandMessage)}.
     */

    @Override
    public void onSuccess(CommandMessage<? extends C> commandMessage, Object result) {
        onSuccess(commandMessage);
    }

    /**
     * Invoked when command handling execution was successful.
     *
     * @param commandMessage The command message successfully executed
     */
    protected abstract void onSuccess(CommandMessage<? extends C> commandMessage);
}
