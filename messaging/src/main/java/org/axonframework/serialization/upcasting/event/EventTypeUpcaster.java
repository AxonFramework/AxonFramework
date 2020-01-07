/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.SimpleSerializedType;

import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link SingleEventUpcaster} implementation which allows for type upcasting only. This could be used if the event's
 * class name did not follow the desired naming convention or if an event's package name has been adjusted.
 * <p>
 * Note that this upcaster <b>should not</b> be used to change the semantic meaning of an event. Such a requirement
 * points towards a new event type instead of adjusting an existing one.
 *
 * @author Steven van Beelen
 * @since 4.3
 */
public abstract class EventTypeUpcaster extends SingleEventUpcaster {

    /**
     * Retrieve the expected event payload type this upcaster should react on.
     *
     * @return the expected event payload type this upcaster should react on
     */
    public abstract String expectedPayloadType();

    /**
     * Retrieve the expected event revision this upcaster should react on.
     *
     * @return the expected event revision this upcaster should react on
     */
    public abstract String expectedRevision();

    /**
     * Retrieve the event payload type to upcast towards.
     *
     * @return the event payload type to upcast towards
     */
    public abstract String upcastedPayloadType();

    /**
     * Retrieve the event revision to upcast towards.
     *
     * @return the event revision to upcast towards
     */
    public abstract String upcastedRevision();

    @Override
    protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
        SerializedType serializedType = intermediateRepresentation.getType();
        return isExpectedPayloadType(serializedType.getName()) && isExpectedRevision(serializedType.getRevision());
    }

    /**
     * Check whether the given {@code payloadType} matches the outcome of {@link #expectedPayloadType()}.
     *
     * @param payloadType the event payload type received by this upcaster in the {@link #canUpcast(IntermediateEventRepresentation)}
     *                    method
     * @return {@code true} if the given {@code payloadType} matches the result of {@link #expectedPayloadType()},
     * {@code false} otherwise
     */
    protected boolean isExpectedPayloadType(String payloadType) {
        return Objects.equals(payloadType, expectedPayloadType());
    }

    /**
     * Check whether the given {@code revision} matches the outcome of {@link #expectedRevision()}.
     *
     * @param revision the event payload type received by this upcaster in the {@link #canUpcast(IntermediateEventRepresentation)}
     *                 method
     * @return {@code true} if the given {@code revision} matches the result of {@link #expectedRevision()}, {@code
     * false} otherwise
     */
    protected boolean isExpectedRevision(String revision) {
        return Objects.equals(revision, expectedRevision());
    }

    @Override
    protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
        return intermediateRepresentation.upcastPayload(upcastedType(), Object.class, Function.identity());
    }

    /**
     * Retrieve the upcasted event {@link SerializedType}. Returns a {@link SimpleSerializedType} using {@link
     * #upcastedPayloadType()} and {@link #upcastedRevision()} as constructor inputs
     *
     * @return the event {@link SerializedType} to upcast to
     */
    protected SerializedType upcastedType() {
        return new SimpleSerializedType(upcastedPayloadType(), upcastedRevision());
    }
}
