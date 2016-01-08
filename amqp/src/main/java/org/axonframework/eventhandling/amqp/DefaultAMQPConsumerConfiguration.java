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
 * Implementation of the AMQPConsumerConfiguration that allows the Queue Name to be configured. The configuration
 * specifies an exclusive consumer and no explicit prefetch count.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultAMQPConsumerConfiguration implements AMQPConsumerConfiguration {

    private final String queueName;

    /**
     * Initializes the configuration with the given <code>queueName</code>.
     *
     * @param queueName The name of the Queue a event processor should connect to
     */
    public DefaultAMQPConsumerConfiguration(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public Boolean getExclusive() {
        return true;
    }

    @Override
    public Integer getPrefetchCount() {
        return null;
    }
}
