/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.messaging.MessageType;

/**
 * Empty {@link EventMessage} implementation without any {@link EventMessage#getPayload() payload}.
 * <p>
 * Only useful to be paired with {@link org.axonframework.messaging.Context} information in an event-specific
 * {@link org.axonframework.messaging.MessageStream} when there is no event to combine it with.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class NoEventMessage extends GenericEventMessage<Void> implements EventMessage<Void> {

    /**
     * The sole instance of the {@link NoEventMessage}.
     */
    public static final NoEventMessage INSTANCE = new NoEventMessage();

    private NoEventMessage() {
        //noinspection DataFlowIssue
        super(new MessageType(NoEventMessage.class), null);
    }
}
