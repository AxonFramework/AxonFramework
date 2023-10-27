/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.lifecycle;

import org.axonframework.messaging.annotation.HasHandlerAttributes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indicating that a member method should be a part of the start cycle of an Axon application. The operation
 * can be made asynchronous by defining a return type of {@link java.util.concurrent.CompletableFuture} on the annotated
 * method.
 * <p>
 * The {@link #phase()} defines the moment the member method should be invoked. The lower the provided {@code phase},
 * the earlier this method will be executed during start up.
 * <p>
 * This annotation is only allowed on methods and as a meta-annotation.
 *
 * @author Steven van Beelen
 * @since 4.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@HasHandlerAttributes
public @interface StartHandler {

    /**
     * An {@code int} defining the moment in the start cycle the member method should be invoked. The lower the provided
     * {@code phase}, the earlier this method will be executed during start up. The {@link Phase} constants can be used
     * to this end.
     *
     * @return the {@code int} defining the moment in the start cycle the member method should be invoked
     */
    int phase();
}

