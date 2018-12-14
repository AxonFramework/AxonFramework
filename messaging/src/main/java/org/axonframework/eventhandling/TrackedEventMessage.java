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

package org.axonframework.eventhandling;

/**
 * Represents an {@link EventMessage} containing a {@link TrackingToken}. The tracking token can be used be {@link
 * EventProcessor event processors} to keep track of which events it has processed.
 *
 * @param <T> The type of payload contained in this Message
 * @author Rene de Waele
 */
public interface TrackedEventMessage<T> extends EventMessage<T> {

    /**
     * Returns the {@link TrackingToken} of the event message.
     *
     * @return the tracking token of the event
     */
    TrackingToken trackingToken();

}
