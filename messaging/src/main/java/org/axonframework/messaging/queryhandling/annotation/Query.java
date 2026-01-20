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

package org.axonframework.messaging.queryhandling.annotation;

import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.annotation.Message;
import org.axonframework.messaging.queryhandling.QueryMessage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark an object as a query.
 * <p>
 * Allows for specifying the business/domain {@link #name()} of the query and the {@link #version()} of the query. The
 * fields are used to map an annotated-query to a {@link QueryMessage}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Message(messageType = QueryMessage.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Query {

    /**
     * The namespace or (bounded) context of the query.
     * <p>
     * Is used to define the {@link QualifiedName#namespace()} of a fully qualified name.
     * <p>
     * Defaults to the package name of the annotated class.
     *
     * @return The namespace or (bounded) context of the query.
     */
    String namespace() default "";

    /**
     * The business or domain name of the query.
     * <p>
     * Is used to define the {@link QualifiedName#localName()} of a fully qualified name.
     * <p>
     * Defaults to the simple name of the annotated class. Note that when an inner class is annotated, the simple name
     * does not include the names of the enclosing classes.
     *
     * @return The business or domain name of the query.
     */
    String name() default "";

    /**
     * The version of the query.
     * <p>
     * Will typically be mapped to the {@link MessageType#version()}. Defaults to {@link MessageType#DEFAULT_VERSION}.
     *
     * @return The version of the query.
     */
    String version() default MessageType.DEFAULT_VERSION;
}
