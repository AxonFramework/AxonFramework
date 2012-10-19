/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.replay;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IncomingMessageHandler implementation that simply discards all messages dispatch during a replay process. This
 * handler is typically useful when not expecting to perform a replay while the cluster is actively listening to events
 * on a command bus, for example when performing an offline replay.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DiscardingIncomingMessageHandler implements IncomingMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(DiscardingIncomingMessageHandler.class);

    @Override
    public void prepareForReplay(Cluster destination) {
    }

    @Override
    public void onIncomingMessages(Cluster destination, EventMessage... messages) {
        if (messages != null && messages.length > 0 && logger.isInfoEnabled()) {
            final StringBuilder msg = new StringBuilder("Discarding ")
                    .append(messages.length)
                    .append(" messages on cluster [")
                    .append(destination.getName())
                    .append("] during an event replay: [");
            boolean firstClass = true;
            for (EventMessage message : messages) {
                if (!firstClass) {
                    msg.append(", ");
                }
                msg.append(message.getPayloadType().getSimpleName());
                firstClass = false;
            }
            msg.append("]");
            logger.info(msg.toString());
        }
    }

    @Override
    public void releaseMessage(DomainEventMessage message) {
        // do nothing
    }

    @Override
    public void processBacklog(Cluster destination) {
        // do nothing
    }

    @Override
    public void onReplayFailed(Cluster destination, RuntimeException cause) {
        // do nothing
    }
}
