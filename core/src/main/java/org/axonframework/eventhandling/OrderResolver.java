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

package org.axonframework.eventhandling;

/**
 * Interface describing a mechanism that provides the order for any given Event Listener. Listeners with a lower order
 * should receive precedence over the instances with a higher value. The order of instances with default order is
 * undefined.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface OrderResolver {

    /**
     * Returns the order for the given {@code listener}.
     * <p/>
     * Implementations should check whether the {@code listener} implements {@link EventListenerProxy}. In that
     * case, use {@link org.axonframework.eventhandling.EventListenerProxy#getTargetType()} to get access to the actual
     * type handling the events.
     *
     * @param listener the listener to resolve the order for
     * @return the order for the given listener, or {@code 0} if no specific order is provided.
     */
    int orderOf(EventListener listener);
}
