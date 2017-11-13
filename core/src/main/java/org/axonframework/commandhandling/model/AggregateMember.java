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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for a field that references one or more Entities capable of handling Commands or Events. The
 * annotation may be placed on a field referencing a single Entity, a field referencing a Collection of Entities, or a
 * Map containing Entities mapped by their identifier.
 * <p>
 * If the annotation is placed on a Collection of Entities, an Entity is selected for Command handling based on the
 * Entity's identifier and the value of the routing key property on the Command. See {@link EntityId} for more
 * information.
 * <p>
 * If the annotation is placed on a Map of Entities, the key of the Map should be equal to the Entity's identifier. Note
 * that Entities in the Map still need to specify which routing key to use. To that end Entities should contain a {@link
 * EntityId} annotated identifier field. Usually it is advantageous in terms of performance to store Entities in a Map
 * instead of Collection.
 */
@Documented
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AggregateMember {

    /**
     * Indicates whether commands should be forwarded to this AggregateMember. Defaults to {@code true}.
     *
     * @return a {@code boolean} denoting iff commands should be forwarded, yes or no.
     */
    boolean forwardCommands() default true;

    /**
     * Indicates whether events should be forwarded to this AggregateMember. Defaults to {@code true}.
     *
     * @return a {@code boolean} denoting if events should be forwarded, yes or no.
     *
     * @deprecated in favor of the {@code eventRoutingMode}.
     */
    @Deprecated
    boolean forwardEvents() default true;

    /**
     * Indicates the routing mode used for events within this entity. Defaults to {@code ForwardingMode.ALL} to allow
     * all events through.
     *
     * @return a {@link org.axonframework.commandhandling.model.ForwardingMode} describing what mode should be used in
     * forwarding events to this Aggregate Member.
     */
    ForwardingMode eventRoutingMode() default ForwardingMode.ALL;

    /**
     * The property of the event to be used as a routing key towards this Aggregate Member.
     *
     * @return The property of the event to use as routing key.
     */
    String eventRoutingKey() default "";

    /**
     * Provides the member's type. By default the type of member is determined from the field's generic type.
     *
     * @return a {@link java.lang.Class} instance of the type of this Aggregate Member
     */
    Class<?> type() default Void.class;
}
