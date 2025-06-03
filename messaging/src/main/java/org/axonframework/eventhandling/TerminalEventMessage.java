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
 * Empty {@link EventMessage} implementation without any {@link EventMessage#getPayload() payload}, used as the
 * <b>terminal</b> message of a {@link org.axonframework.messaging.MessageStream}.
 * <p>
 * Only useful to be paired with {@link org.axonframework.messaging.Context} information in an event-specific
 * {@code MessageStream} when there is no event payload to combine it with.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class TerminalEventMessage extends GenericEventMessage<Void> implements EventMessage<Void> {

    /**
     * The sole instance of the {@link TerminalEventMessage}.
     */
    public static final TerminalEventMessage INSTANCE = new TerminalEventMessage();

    private TerminalEventMessage() {
        //noinspection DataFlowIssue
        super(new MessageType(TerminalEventMessage.class), null);
    }
}
