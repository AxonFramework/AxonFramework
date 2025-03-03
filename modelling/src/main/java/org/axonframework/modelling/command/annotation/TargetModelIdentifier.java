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

package org.axonframework.modelling.command.annotation;

import org.axonframework.commandhandling.annotation.RoutingKey;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to be placed on a parameter of a {@link org.axonframework.messaging.MessageHandler} method that should
 * receive a model loaded from the {@link org.axonframework.modelling.command.ModelRegistry}. The parameter should be of
 * the type of the model to inject.
 * <p>
 * Unless a specific {@code idResolver} is specified, the {@link AnnotationBasedModelIdentifierResolver} is used to resolve the
 * model identifier from the message. This is based on finding a {@link TargetModelIdentifier} annotation on a field or
 * accessor method of the message payload.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@RoutingKey
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TargetModelIdentifier {

}
