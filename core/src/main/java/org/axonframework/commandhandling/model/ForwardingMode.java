/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.commandhandling.model;

/**
 * Enumeration describing the possible forwarding modes usable. Is for example used in the {@link
 * org.axonframework.commandhandling.model.AggregateMember} to describe how events should be routed to it.
 */
public enum ForwardingMode {

    /**
     * Forwards all the messages.
     */
    ALL,
    /**
     * Forwards none of the messages.
     */
    NONE,
    /**
     * Forwards messages based on a routing key value.
     */
    ROUTING_KEY

}
