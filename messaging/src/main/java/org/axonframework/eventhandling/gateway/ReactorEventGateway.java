/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling.gateway;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.reactive.ReactorMessageDispatchInterceptorSupport;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

/**
 * Variation of the {@link EventGateway}, wrapping a {@link EventBus} for a friendlier API. Provides support for reactive return types such as {@link Flux} from Project
 * Reactor.
 *
 * @author Milan Savic
 * @since 4.4
 */
public interface ReactorEventGateway extends ReactorMessageDispatchInterceptorSupport<EventMessage<?>> {

    /**
     * Publishes given {@code events} once the caller subscribes to the resulting Flux. Returns immediately.
     * <p/>
     * Given {@code events} are wrapped as payloads of a {@link EventMessage} that are eventually published on the
     * {@link EventBus}, unless {@code event} already implements {@link Message}. In that case, a {@code EventMessage}
     * is constructed from that message's payload and {@link org.axonframework.messaging.MetaData}.
     *
     * @param events events to be published
     * @return events that were published. DO NOTE: if there were some interceptors registered to this {@code gateway},
     * they will be processed first, before returning events to the caller. The order of returned events is the same as
     * one provided as the input parameter.
     */
    default Flux<Object> publish(Object... events) { // NOSONAR
        return publish(Arrays.asList(events));
    }

    /**
     * Publishes given {@code events} once the caller subscribes to the resulting Flux. Returns immediately.
     * <p/>
     * Given {@code events} are wrapped as payloads of a {@link EventMessage} that are eventually published on the
     * {@link EventBus}, unless {@code event} already implements {@link Message}. In that case, a {@code EventMessage}
     * is constructed from that message's payload and {@link org.axonframework.messaging.MetaData}.
     *
     * @param events the list of events to be published
     * @return events that were published. DO NOTE: if there were some interceptors registered to this {@code gateway},
     * they will be processed first, before returning events to the caller. The order of returned events is the same as
     * one provided as the input parameter.
     */
    Flux<Object> publish(List<?> events);

    /**
     * Publishes given {@code events} once the caller subscribes to the resulting Flux. Returns immediately.
     * <p/>
     * Given {@code events} are wrapped as payloads of a {@link EventMessage} that are eventually published on the
     * {@link EventBus}, unless {@code event} already implements {@link Message}. In that case, a {@code EventMessage}
     * is constructed from that message's payload and {@link org.axonframework.messaging.MetaData}.
     *
     * @param events the publisher of events to be published
     * @return events that were published. DO NOTE: if there were some interceptors registered to this {@code gateway},
     * they will be processed first, before returning events to the caller. The order of returned events is the same as
     * one provided as the input parameter.
     */
    default Flux<Object> publishAll(Publisher<?> events) {
        return Flux.from(events)
                   .concatMap(this::publish);
    }
}
