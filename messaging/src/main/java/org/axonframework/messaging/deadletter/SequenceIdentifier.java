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

import java.io.Serializable;

/**
 * Interface describing an identifier for sequences maintained by a {@link SequencedDeadLetterQueue}.
 * <p>
 * Describes a {@link #identifier()} of the sequence, as well as a {@link #group()} the sequence belongs to. The
 * {@code identifier} may, for example, refer to a message ordering, whereas the {@code group} can describe a specific
 * message processing component.
 *
 * @author Steven van Beelen
 * @see SequencedDeadLetterQueue
 * @since 4.6.0
 */
public interface SequenceIdentifier extends Serializable, Comparable<SequenceIdentifier> {

    /**
     * The identifier of this sequence. May refer to a message ordering.
     *
     * @return The identifier of this sequence.
     */
    Object identifier();

    /**
     * The group this sequence belongs to. May refer to a specific message processing component.
     *
     * @return The group this sequence belongs to.
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
    default int compareTo(SequenceIdentifier other) {
        return this.combinedIdentifier().compareTo(other.combinedIdentifier());
    }
}
