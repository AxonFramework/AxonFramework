/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.distributed;

/**
 * @author Allard Buijze
 * @since 2.0
 */
public enum UnresolvedRoutingKeyPolicy {

    /**
     * Policy that indicates that Routing Key resolution should fail with an Exception when no routing key can be found
     * for a Command Message.
     * <p/>
     * When the routing key is based on static content in the Command Message, the exception raised should extend from
     * {@link org.axonframework.common.AxonNonTransientException} to indicate that retries do not have a chance to
     * succeed.
     */
    ERROR,

    /**
     * Policy that indicates a random key is to be returned when no Routing Key can be found for a Command Message.
     * Although not required to be fully random, implementations are required to return a different key for each
     * incoming command. Multiple invocations for the same command message may return the same value, but are not
     * required to do so.
     * <p/>
     * This effectively means the Command Message is routed to a random segment.
     */
    RANDOM_KEY,

    /**
     * Policy that indicates a fixed key ("unresolved") should be returned when no Routing Key can be found for a
     * Command Message. This effectively means all Command Messages with unresolved routing keys are routed to a the
     * same segment. The load of that segment may therefore not match the load factor.
     */
    STATIC_KEY

}
