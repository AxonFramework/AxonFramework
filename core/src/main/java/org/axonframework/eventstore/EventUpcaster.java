/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.eventstore;

/**
 * Interface for Upcasters. An upcaster is the mechanism used to convert deprecated (typically serialized) events from
 * the eventstore and convert them into the current format. If the serializer itself is not able to cope with the
 * changed formats (XStream, for example, will allow for some changes by using aliases), the Upcaster will allow you to
 * configure more complex structural transformations.
 *
 * @author Allard Buijze
 * @param <T> The data format that this upcaster uses to represent the event
 * @since 0.7
 */
public interface EventUpcaster<T> {

    /**
     * Since Java's generics are erased, this method allows serializers to verify the supported event representation of
     * this upcaster. Serializers may support any number of formats, and are responsible for the transformation between
     * them if they accept more than one.
     *
     * @return The type of representation supported by this upcaster
     */
    Class<T> getSupportedRepresentation();

    /**
     * Upcast the given <code>event</code> to make it parsable by the EventSerializer. Implementations may alter the
     * given <code>event</code>, and return a reference to the same instance.
     *
     * @param event The serialized event. This instance may have been processed by other upcasters.
     * @return The instance to use for further upcasting or deserialization
     */
    T upcast(T event);

}
