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

package org.axonframework.integration.eventbus;

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;

/**
 * Adapter that allows an EventListener to be registered as a Spring Integration {@link MessageHandler}.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public class MessageHandlerAdapter implements MessageHandler {

    private final EventListener eventListener;

    /**
     * Initialize an adapter for the given <code>eventListener</code>.
     *
     * @param eventListener the event listener to adapt
     */
    public MessageHandlerAdapter(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleMessage(Message<?> message) {
        Object event = message.getPayload();
        eventListener.handle(new GenericEventMessage<Object>(event, message.getHeaders()));
    }
}
