/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.deadletter;

/**
 * Interface describing an identifier for queues maintained by a {@link DeadLetterQueue}.
 * <p>
 * Describes a {@link #identifier()} of the queue, as well as a {@link #group()} the queue belongs to. The {@code
 * identifier} may, for example, refer to a sequence of messages, whereas the {@code group} can describe a specific
 * message processing component.
 *
 * @author Steven van Beelen
 * @see DeadLetterQueue
 * @since 4.6.0
 */
public interface QueueIdentifier extends Comparable<QueueIdentifier> {

    /**
     * The identifier of this queue. May refer to a sequence of messages.
     *
     * @return The identifier of this queue.
     */
    Object identifier();

    /**
     * The group this queue belongs to. May refer to a specific message processing component.
     *
     * @return The group this queue belongs to.
     */
    String group();

    /**
     * Return a combination of the {@link #identifier()} and {@link #group()}, separated by a dash.
     *
     * @return A combination of the {@link #identifier()} and {@link #group()}, separated by a dash.
     */
    default String combinedIdentifier() {
        return identifier().toString() + "-" + group();
    }

    @Override
    default int compareTo(QueueIdentifier other) {
        return this.combinedIdentifier().compareTo(other.combinedIdentifier());
    }
}
