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

/**
 * Interface of a mechanism that receives Messages dispatched to a Cluster that is in Replay mode. The implementation
 * defines if, how and when the cluster should handle events while a replay is in progress.
 * <p/>
 * When replying is finished, the handler is asked to flush any backlog it may have gathered during the replay.
 * <p/>
 * Implementations must ensure thread safety. The {@link #prepareForReplay(org.axonframework.eventhandling.Cluster)},
 * {@link #releaseMessage(org.axonframework.domain.DomainEventMessage)} and {@link
 * #processBacklog(org.axonframework.eventhandling.Cluster)} methods are invoked by the thread performing the replay.
 * The {@link #onIncomingMessages(org.axonframework.eventhandling.Cluster, org.axonframework.domain.EventMessage[])}
 * method is invoked in the thread that attempts to publish events to a cluster while it is in replay mode.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface IncomingMessageHandler {

    /**
     * Invoked just before replay mode is activated. Any messages passed to
     * {@link #onIncomingMessages(org.axonframework.eventhandling.Cluster, org.axonframework.domain.EventMessage[])}
     * prior to this method invocation should be dispatched immediately to the destination cluster to prevent message
     * loss.
     * <p/>
     * This method is invoked in the thread that executes the replay process.
     *
     * @param destination The cluster on which events are about te be replayed
     */
    void prepareForReplay(Cluster destination);

    /**
     * Invoked while the ReplayingCluster is in replay mode and an Event is being dispatched to the Cluster. If the
     * timestamp of the given <code>message</code> is before the timestamp of any message reported via {@link
     * #releaseMessage(org.axonframework.domain.DomainEventMessage)}, consider discarding the incoming message.
     * <p/>
     * This method is invoked in the thread that attempts to publish the given messages to the given destination.
     *
     * @param destination The cluster to receive the message
     * @param messages    The messages to dispatch to the cluster
     */
    void onIncomingMessages(Cluster destination, EventMessage... messages);

    /**
     * Invoked when a message has been replayed from the event store. If such a message has been received with {@link
     * #onIncomingMessages(org.axonframework.eventhandling.Cluster, org.axonframework.domain.EventMessage[])}, it
     * should
     * be discarded.
     * <p/>
     * After this invocation, any invocation of {@link #onIncomingMessages(org.axonframework.eventhandling.Cluster,
     * org.axonframework.domain.EventMessage[])} with a message who's timestamp is lower that this message's timestamp
     * can be safely discarded.
     * <p/>
     * This method is invoked in the thread that executes the replay process
     *
     * @param message The message replayed from the event store
     */
    void releaseMessage(DomainEventMessage message);

    /**
     * Invoked when all events from the Event Store have been processed. Any remaining backlog, as well as any messages
     * received through {@link #onIncomingMessages(org.axonframework.eventhandling.Cluster,
     * org.axonframework.domain.EventMessage[])} should be dispatched to the given <code>delegate</code>. Transactions
     * started by the replay process have been committed or rolled back prior to the invocation of this method.
     * <p/>
     * Note that {@link #onIncomingMessages(org.axonframework.eventhandling.Cluster,
     * org.axonframework.domain.EventMessage[])} may be invoked during or after the invocation of this method. These
     * messages <em>must</em> be dispatched by this handler to prevent message loss.
     * <p/>
     * This method is invoked in the thread that executes the replay process
     *
     * @param destination The destination cluster to dispatch backlogged messages to
     */
    void processBacklog(Cluster destination);

    /**
     * Invoked when a replay has failed. Typically, this means the state of the cluster's backing data source cannot be
     * guaranteed, and the replay should be retried.
     *
     * @param destination The destination cluster to dispatch backlogged messages to, if appropriate in this scenario
     * @param cause       The cause of the failure
     */
    void onReplayFailed(Cluster destination, Throwable cause);
}
