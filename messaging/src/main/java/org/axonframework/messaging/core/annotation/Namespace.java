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

package org.axonframework.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.axonframework.messaging.queryhandling.annotation.Query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to describe the namespace (or bounded context) of a class, a package, or an entire module.
 * <p>
 * The namespace value is, for example, used to derive the {@link QualifiedName#namespace()} field whenever a
 * {@link QualifiedName} is to be constructed while annotation resolving components are used. By being applicable to
 * packages and modules,  it provides a single location to adjust the namespace for a large number of classes in one go.
 * This is particularly useful when messages are grouped within a package or module, which all require the same
 * namespace field. As such, it is a catch-all compared to the type-specific {@link Command#namespace()},
 * {@link Event#namespace()}, and {@link Query#namespace()} annotations.
 * <p>
 * Example usage on a class:
 * <pre>{@code
 * @Namespace("university")
 * public record SubscribeStudentToCourse(...) { }
 * }</pre>
 * <p>
 * Example usage on a package (in {@code package-info.java}):
 * <pre>{@code
 * @Namespace("university")
 * package org.axonframework.examples.university;
 *
 * import org.axonframework.messaging.core.annotation.Namespace;
 * }</pre>
 *
 * @author Steven van Beelen
 * @see AnnotationMessageTypeResolver
 * @since 5.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE, ElementType.MODULE, ElementType.ANNOTATION_TYPE})
public @interface Namespace {

    /**
     * The namespace (or bounded context) defined by this annotation.
     *
     * @return the namespace (or bounded context) defined by this annotation
     */
    @Nonnull
    String value() default "";
}
