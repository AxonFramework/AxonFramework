/*
 * Copyright (c) 2010-2023. Axon Framework
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * Interface for a source of {@link EventMessage EventMessages} to which event processors can subscribe.
 * <p>
 * Provides functionality to {@link #subscribe(Consumer) subscribe} event batch consumers to receive
 * {@link EventMessage events} published to this source. When subscribed, consumers will receive all events published to
 * this source since the subscription.
 * <p>
 * This interface is the replacement for the deprecated {@link org.axonframework.messaging.SubscribableEventSource},
 * focusing specifically on event message handling.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface SubscribableEventSource {

    /**
     * Subscribe the given {@code eventsBatchConsumer} to this event source. When subscribed, it will receive all events
     * published to this source since the subscription.
     * <p>
     * If the given {@code eventsBatchConsumer} is already subscribed, nothing happens.
     *
     * @param eventsBatchConsumer The event batches consumer to subscribe.
     * @return A {@link Registration} handle to unsubscribe the {@code eventsBatchConsumer}. When unsubscribed, it will
     * no longer receive events.
     */
    Registration subscribe(@Nonnull BiConsumer<List<? extends EventMessage>, ProcessingContext> eventsBatchConsumer);
    // TODO: BiFunction --- return CompletableFuture<Void>
}
