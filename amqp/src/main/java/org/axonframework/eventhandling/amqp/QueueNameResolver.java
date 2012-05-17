/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.amqp;

import org.axonframework.eventhandling.Cluster;

/**
 * The QueueName resolver provides the name of the AMQP Queue a cluster should get its Event Messages from. Multiple
 * clusters may resolve to the same queue name. In that case it is up to the Terminal implementation to decide whether
 * they compete for messages on the same queue, or both receive a copy of each message.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface QueueNameResolver {

    /**
     * Resolves the name of the Queue to read messages from for the given <code>cluster</code>.
     *
     * @param cluster The cluster to resolve the queue name for
     * @return The name of the Queue to read messages from
     */
    String resolveQueueName(Cluster cluster);
}
