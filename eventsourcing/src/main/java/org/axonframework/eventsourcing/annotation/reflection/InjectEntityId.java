/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.annotation.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated parameter should be injected with the entity identifier of the
 * {@link EntityCreator}-annotated method or constructor being invoked.
 * <p>
 * This annotation is necessary due to potential ambiguities, where, for example, both the id and the payload of the
 * first event can be a {@link String}. As throughout the framework the first parameter without an annotation is assumed
 * to be the payload, this annotation is used to indicate that the parameter should be injected with the entity
 * identifier instead.
 *
 * @author Mitchell Herrijgers
 * @see EntityCreator
 * @since 5.0.0
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectEntityId {

}
