/*
 * Copyright (c) 2010-2015. Axon Framework
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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventsourcing.DomainEventMessage;

import java.util.List;

/**
 * Interface of a mechanism that receives Messages dispatched to an EventProcessor that is in Replay mode. The implementation
 * defines if, how and when the event processor should handle events while a replay is in progress.
 * <p/>
 * When replying is finished, the handler is asked to flush any backlog it may have gathered during the replay.
 * <p/>
 * Implementations must ensure thread safety. The {@link #prepareForReplay(EventProcessor)},
 * {@link #releaseMessage(EventProcessor, DomainEventMessage)} and
 * {@link #processBacklog(EventProcessor)} methods are invoked by the thread performing the
 * replay. The {@link #onIncomingMessages(EventProcessor, List)} method is invoked in the thread that attempts to publish
 * events to a event processor while it is in replay mode.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface IncomingMessageHandler {

    /**
     * Invoked just before replay mode is activated. Any messages passed to
     * {@link #onIncomingMessages(EventProcessor, List)} prior to this method invocation should be dispatched immediately to
     * the destination event processor to prevent message loss.
     * <p/>
     * This method is invoked in the thread that executes the replay process.
     *
     * @param destination The event processor on which events are about te be replayed
     */
    void prepareForReplay(EventProcessor destination);

    /**
     * Invoked while the ReplayingEventProcessor is in replay mode and an Event is being dispatched to the
     * EventProcessor. If the timestamp of the given <code>message</code> is before the timestamp of any message
     * reported via {@link #releaseMessage(EventProcessor, DomainEventMessage)}, consider
     * discarding the incoming message.
     * <p/>
     * This method returns the list of messages that must be considered as handled. May be <code>null</code> to
     * indicate all given <code>messages</code> have been stored for processing later.
     * <p/>
     * This method is invoked in the thread that attempts to publish the given messages to the given destination.
     *
     * @param destination The event processor to receive the message
     * @param messages    The messages to dispatch to the event processor
     * @return a list of messages that may be considered as handled
     */
    List<EventMessage<?>> onIncomingMessages(EventProcessor destination, List<EventMessage<?>> messages);

    /**
     * Invoked when a message has been replayed from the event store. If such a message has been received with {@link
     * #onIncomingMessages(EventProcessor, List)}, it should be discarded.
     * <p/>
     * After this invocation, any invocation of {@link #onIncomingMessages(EventProcessor, List)} with a message who's
     * timestamp (minus a safety buffer to account for clock differences) is lower that this message's timestamp can be
     * safely discarded. It is recommended that non-Domain EventMessages in the backlog are forwarded to the
     * event processor provided, instead of discarded. They must then also be included in the returned list.
     * <p/>
     * This method returns the list of EventMessages that must be considered processed, regardless of whether they have
     * been forwarded to the original <code>destination</code> or not. These EventMessages have been registered in a
     * call to {@link #onIncomingMessages(EventProcessor, List)}.
     * <p/>
     * It is highly recommended to return the instance used in the {@link #onIncomingMessages(EventProcessor, List)}
     * invocation, over the given <code>message</code>, even if they refer to
     * the save Event.
     * <p/>
     * This method is invoked in the thread that executes the replay process
     *
     * @param destination The original destination of the message to be released
     * @param message     The message replayed from the event store
     * @return The list of messages that have been released
     */
    List<EventMessage> releaseMessage(EventProcessor destination, DomainEventMessage message);

    /**
     * Invoked when all events from the Event Store have been processed. Any remaining backlog, as well as any messages
     * received through {@link #onIncomingMessages(EventProcessor, List)} should be dispatched to the given
     * <code>delegate</code>. Transactions started by the replay process have been committed or rolled back prior to
     * the
     * invocation of this method.
     * <p/>
     * Note that {@link #onIncomingMessages(EventProcessor, List)} may be invoked during or after the invocation of this
     * method. These messages <em>must</em> be dispatched by this handler to prevent message loss.
     * <p/>
     * This method is invoked in the thread that executes the replay process
     *
     * @param destination The destination event processor to dispatch backlogged messages to
     */
    void processBacklog(EventProcessor destination);

    /**
     * Invoked when a replay has failed. Typically, this means the state of the event processor's backing data source cannot be
     * guaranteed, and the replay should be retried.
     *
     * @param destination The destination event processor to dispatch backlogged messages to, if appropriate in this scenario
     * @param cause       The cause of the failure
     */
    void onReplayFailed(EventProcessor destination, Throwable cause);
}
