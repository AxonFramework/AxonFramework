/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.messaging;

/**
 * A contract towards being a distributed message bus implementation. The generic {@code T} defines the type of message
 * bus which is distributed.
 *
 * @param <MessageBus> the message bus which is distributed
 * @author Steven van Beelen
 * @since 4.2.2
 */
public interface Distributed<MessageBus> {

    /**
     * Return the message bus of type {@code MessageBus} which is regarded as the local segment for this implementation.
     * Would return the message bus used to dispatch and handle messages in a local environment to bridge the gap in a
     * distributed set up.
     *
     * @return a {@code MessageBus} which is the local segment for this distributed message bus implementation
     */
    MessageBus localSegment();
}
