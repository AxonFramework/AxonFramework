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

import org.axonframework.modelling.command.ModelIdentifierResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to be placed on a parameter of a {@link org.axonframework.messaging.MessageHandler} method that should
 * receive a model loaded from the {@link org.axonframework.modelling.ModelRegistry}. The parameter should be of
 * the type of the model to inject.
 * <p>
 * The {@code idProperty} attribute can be used to specify the property of the message payload that contains the
 * identifier of the model to inject. If not specified, the {@code idResolver} is used to resolve the identifier of the
 * model to inject.
 * <p>
 * Unless a specific {@code idResolver} is specified, the {@link AnnotationBasedModelIdentifierResolver} is used to resolve the
 * model identifier from the message. This is based on finding a {@link TargetModelIdentifier} annotation on a field or
 * accessor method of the message payload.
 * <p>
 * So, identifiers will be resolved in the following order:
 * <ol>
 *     <li>From the property specified in {@code idProperty}.</li>
 *     <li>From the {@code idResolver}.</li>
 *     <li>From the {@link TargetModelIdentifier} annotation on the message payload.</li>
 * </ol>
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectModel {

    /**
     * The property of the message payload that contains the identifier of the model to inject. If not specified, the
     * {@code idResolver} is used to resolve the identifier of the model to inject.
     *
     * @return the property of the message payload that contains the identifier of the model to inject.
     */
    String idProperty() default "";

    /**
     * The {@link ModelIdentifierResolver} to resolve the identifier of the model to inject. Should have a no-arg constructor.
     *
     * @return the {@link ModelIdentifierResolver} to resolve the identifier of the model to inject.
     */
    Class<? extends ModelIdentifierResolver<?>> idResolver() default AnnotationBasedModelIdentifierResolver.class;
}
