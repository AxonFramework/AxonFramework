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

package org.axonframework.commandhandling.annotation;

import org.axonframework.eventsourcing.annotation.AbstractAnnotatedEntity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for fields that contain a {@link java.util.Map} of Entities capable of handling Commands on behalf of the aggregate. When
 * a field is annotated with <code>@CommandHandlerMemberMap</code>, it is a hint towards Command Handler discovery
 * mechanisms that the entity should also be inspected for {@link org.axonframework.commandhandling.annotation.CommandHandler} annotated methods.
 * <p/>
 * Note that CommandHandler detection is done using static typing. This means that only the declared type of the field
 * can be inspected. If a subclass of that type is assigned to the field, any handlers declared on that subclass will
 * be ignored.
 *
 * @author Jeroen Bruinink
 * @since 2.4
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface CommandHandlingMemberMap {

    /**
     * The name of the property on the incoming command's payload that identifies the intended target of the command.
     * This identity should correspond to the keys in the annotated map.
     * <p/>
     * The name of this method must correspond with the getter method using the JavaBean specification (property 'id'
     * is accessed using method 'getId()'), or any other specification supported by a configured
     * {@link org.axonframework.common.property.PropertyAccessStrategy}.
     */
    String commandTargetProperty();

    /**
     * The type of entity contained in the annotated map. By default, Axon attempts to identify the type by the
     * generic parameters on the field declaration.
     */
    Class<? extends AbstractAnnotatedEntity> entityType() default AbstractAnnotatedEntity.class;
}
