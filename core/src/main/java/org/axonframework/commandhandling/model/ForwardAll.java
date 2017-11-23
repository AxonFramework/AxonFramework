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

package org.axonframework.commandhandling.model;

import org.axonframework.messaging.Message;

import java.util.function.Supplier;

/**
 * Forward all messages {@code T} regardless of their set up.
 *
 * @param <T> the implementation {@code T} of the {@link org.axonframework.messaging.Message} being forwarded.
 */
public class ForwardAll<T extends Message<?>> implements ForwardingMode<T> {

    public static final ForwardAll INSTANCE = new ForwardAll();

    @Override
    public ForwardingMode getInstance(Supplier<ForwardingMode> forwardingModeConstructor) {
        return forwardingModeConstructor.get();
    }

    @Override
    public <E> boolean forwardMessage(T message, E target) {
        return true;
    }
}
