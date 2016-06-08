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

package org.axonframework.spring.messaging.eventbus;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adapter that allows an EventListener to be registered as a Spring Messaging {@link MessageHandler}.
 *
 * @author Allard Buijze
 * @since 2.3.1
 */
public class MessageHandlerAdapter implements MessageHandler {

    private final Consumer<List<? extends EventMessage<?>>> eventProcessor;

    /**
     * Initialize an adapter for the given <code>eventProcessor</code>.
     *
     * @param eventProcessor the event processor to adapt
     */
    public MessageHandlerAdapter(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleMessage(Message<?> message) {
        eventProcessor.accept(Collections.singletonList(
                new GenericEventMessage<>(message.getPayload(), message.getHeaders())));
    }
}
