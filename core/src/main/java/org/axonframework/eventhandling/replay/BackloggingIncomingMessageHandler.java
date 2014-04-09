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

package org.axonframework.eventhandling.replay;

import org.axonframework.common.Assert;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * IncomingMessageHandler implementation that maintains a backlog of all Events published to a cluster while it is
 * in replay mode. When the replay finishes, events in the backlog are processed. Events that have been replayed will
 * be removed from the backlog, to prevent duplicate publication of events.
 * <p/>
 * When a replay fails, the backlog is cleared. This means backlogged items will not be forwarded to the cluster for
 * handling.
 * <p/>
 * Note that a single BackloggingIncomingMessageHandler should *not* be used for multiple Clusters. Each cluster must
 * have its own instance of BackloggingIncomingMessageHandler.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class BackloggingIncomingMessageHandler implements IncomingMessageHandler {

    private boolean inReplay = false;
    private final Queue<EventMessage> backlog;
    private final Set<String> replayedMessages = new HashSet<String>();
    private DateTime backlogThreshold;
    private final Duration timeMargin;


    /**
     * Creates a new BackloggingIncomingMessageHandler. Event Messages that have been generated more than 5 seconds
     * before the start of the replay are not placed in the backlog, as they are assumed to be processed by the replay
     * process. If the latency of the replayed cluster is expected to be more than 5 seconds, use the {@link
     * #BackloggingIncomingMessageHandler(org.joda.time.Duration)} constructor to provide a margin that better suits
     * the latency.
     */
    public BackloggingIncomingMessageHandler() {
        this(Duration.standardSeconds(5));
    }

    /**
     * Creates a new BackloggingIncomingMessageHandler. The given <code>backlogThresholdMargin</code> indicates the
     * age that an event may have to be backlogged. Older events will not be backlogged, as we may assume they will be
     * part of the replay process.
     * <p/>
     * This margin is also used to optimize the lookup of Events in the backlog. Events are only looked up in the
     * portion of the queue that contains events with a timestamp near that of the event being looked for.
     * <p/>
     * A good starting value for this is the maximum expected latency of incoming events.
     *
     * @param backlogThresholdMargin The margin of time to take into account when backlogging events.
     */
    public BackloggingIncomingMessageHandler(Duration backlogThresholdMargin) {
        this(backlogThresholdMargin, new LinkedList<EventMessage>());
    }

    /**
     * Creates a new BackloggingIncomingMessageHandler. The given <code>backlogThresholdMargin</code> indicates the
     * age that an event may have to be backlogged. Older events will not be backlogged, as we may assume they will be
     * part of the replay process.
     *
     * The given <code>backlog</code> queue is used to store backlogged events.
     * <p/>
     * This margin is also used to optimize the lookup of Events in the backlog. Events are only looked up in the
     * portion of the queue that contains events with a timestamp near that of the event being looked for.
     * <p/>
     * A good starting value for this is the maximum expected latency of incoming events.
     *
     * @param backlogThresholdMargin The margin of time to take into account when backlogging events.
     * @param backlog The queue instance used to store backlogged events.
     */
    public BackloggingIncomingMessageHandler(Duration backlogThresholdMargin, Queue<EventMessage> backlog) {
        this.timeMargin = backlogThresholdMargin;
        this.backlog = backlog;
    }

    @Override
    public synchronized void prepareForReplay(Cluster destination) {
        Assert.isFalse(inReplay, "This message handler is already performing a replay. "
                + "Are you using the same instances on multiple clusters?");
        inReplay = true;
        backlogThreshold = new DateTime().minus(timeMargin); // NOSONAR - Partially synchronization variable
    }

    @Override
    public synchronized List<EventMessage> onIncomingMessages(Cluster destination, EventMessage... messages) {
        if (!inReplay) {
            destination.publish(messages);
            return null;
        }
        List<EventMessage> discarded = null;
        for (EventMessage message : messages) {
            if (message.getTimestamp().isAfter(backlogThreshold)) {
                if (replayedMessages.contains(message.getIdentifier())) {
                    if (discarded == null) {
                        discarded = new ArrayList<EventMessage>();
                    }
                    discarded.add(message);
                } else {
                    backlog.add(message);
                }
            }
        }
        return discarded;
    }

    @Override
    public List<EventMessage> releaseMessage(Cluster destination, DomainEventMessage message) {
        List<EventMessage> processedMessages = new LinkedList<EventMessage>();
        if (message.getTimestamp().isAfter(backlogThreshold)) { // NOSONAR - Synchronization not needed here
            replayedMessages.add(message.getIdentifier());
            for (EventMessage backloggedMessage : backlog) {
                if (!backloggedMessage.getTimestamp().isAfter(message.getTimestamp())
                        && !(backloggedMessage instanceof DomainEventMessage)) {

                    processedMessages.add(backloggedMessage);
                    destination.publish(backloggedMessage);
                } else if (backloggedMessage.getIdentifier().equals(message.getIdentifier())) {
                    processedMessages.add(backloggedMessage);
                }

                if (backloggedMessage.getTimestamp().isAfter(message.getTimestamp().plus(timeMargin))) {
                    break;
                }
            }
        }
        // this is faster than removeAll(processedMessages), since we know each message exists at most once, and
        // they're most likely to be near the head of the queue.
        for (EventMessage processedMessage : processedMessages) {
            backlog.remove(processedMessage);
        }
        return processedMessages;
    }

    @Override
    public synchronized void onReplayFailed(Cluster destination, Throwable cause) {
        inReplay = false;
        replayedMessages.clear();
        backlog.clear();
    }

    @Override
    public synchronized void processBacklog(Cluster destination) {
        inReplay = false;
        while (!backlog.isEmpty()) {
            EventMessage message = backlog.poll();
            if (message != null && !replayedMessages.contains(message.getIdentifier())) {
                destination.publish(message);
            }
        }
        replayedMessages.clear();
    }
}
