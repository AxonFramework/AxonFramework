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

package org.axonframework.eventhandling.amqp;

/**
 * @author Allard Buijze
 */
public interface AMQPConsumerConfiguration {

    /**
     * The key of the property in the Cluster Meta Data that reflects the AMQPConsumerConfiguration instance for that
     * cluster
     */
    String AMQP_CONFIG_PROPERTY = "AMQP.Config";

    /**
     * Returns the Queue Name the Cluster should be connected to, or <code>null</code> if no explicit cluster is
     * configured.
     *
     * @return the Queue the cluster should be connected to, or <code>null</code> to revert to a default
     */
    String getQueueName();

    /**
     * Indicates whether this Cluster wishes to be an exclusive consumer on a Queue. <code>null</code> indicated that
     * no explicit preference is provided, and a default should be used.
     *
     * @return the exclusivity indicator for this cluster
     */
    Boolean getExclusive();

    /**
     * Indicates how many messages this Cluster's connector may read read from the Queue before expecting messages to
     * be acknowledged. <code>null</code> means no specific value is provided and a default should be used.
     *
     * @return the number of messages a Cluster's connector may read ahead before waiting for acknowledgements.
     */
    Integer getPrefetchCount();
}
