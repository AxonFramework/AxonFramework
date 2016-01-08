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

package org.axonframework.spring.config.eventhandling;

import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventProcessor;

/**
 * The EventProcessorSelector defines the mechanism that assigns each of the subscribed listeners to an EventProcessor
 * instance. The selector does *not* need to subscribe the listener to that event processor.
 * <p/>
 * <em>Thread safety note:</em> The implementation is expected to be thread safe.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface EventProcessorSelector {

    /**
     * Selects the event processor instance that the given {@code eventListener} should be member of. This may be an existing
     * (or pre-configured) event processor, as well as a newly created event processor.
     * <p/>
     * When {@code null} is returned, this may cause the Event Listener not to be subscribed to any event processor at all.
     *
     * @param eventListener the event listener to select a event processor for
     * @return The event processor assigned to the listener
     */
    EventProcessor selectEventProcessor(EventListener eventListener);
}
