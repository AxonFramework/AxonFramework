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

package org.axonframework.modelling.annotation;

import org.axonframework.messaging.commandhandling.annotation.RoutingKey;
import org.axonframework.messaging.core.Message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to be placed on a parameter of a field or method of the payload of a
 * {@link Message}, which provides the identifier of the target entity when using the
 * {@link AnnotationBasedEntityIdResolver}.
 * <p>
 * See the {@link InjectEntity} annotation for more information about the different ways to resolve the entity id when
 * injecting entities into messsage handlers.
 * <p>
 * Multiple parameters annotated with {@link TargetEntityId} are allowed, but only one distinct non-null value may be
 * returned. If multiple non-null values are found that don't match, or no non-null values were found, a
 * {@link EntityIdResolutionException} is thrown.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@RoutingKey
public @interface TargetEntityId {

}
