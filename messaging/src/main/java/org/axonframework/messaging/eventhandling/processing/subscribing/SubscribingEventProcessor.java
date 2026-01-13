/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.subscribing;

import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.processing.EventProcessor;

/**
 * An {@link EventProcessor} that {@link EventBus#subscribe subscribes} to an event source for events. Events published
 * on the event source are supplied to this processor in the publishing thread.
 * <p>
 * Unlike {@link org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor streaming
 * processors}, subscribing processors do not maintain their own position in the event stream. They process events as
 * they are published, making them suitable for scenarios where immediate processing is required and replay
 * functionality is not needed.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 * @see SimpleSubscribingEventProcessor
 */
public interface SubscribingEventProcessor extends EventProcessor {

}
