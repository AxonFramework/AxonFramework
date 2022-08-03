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

package org.axonframework.messaging;

import org.axonframework.common.Registration;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Interface for a source of {@link Message messages} to which message processors can subscribe.
 *
 * @param <M> the message type
 * @author Allard Buijze
 * @since 3.0
 */
public interface SubscribableMessageSource<M extends Message<?>> {

    /**
     * Subscribe the given {@code messageProcessor} to this message source. When subscribed, it will receive all
     * messages published to this source.
     * <p>
     * If the given {@code messageProcessor} is already subscribed, nothing happens.
     *
     * @param messageProcessor The message processor to subscribe
     * @return a handle to unsubscribe the {@code messageProcessor}. When unsubscribed it will no longer receive
     * messages.
     */
    Registration subscribe(@Nonnull Consumer<List<? extends M>> messageProcessor);
}
