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

package org.axonframework.modelling.command;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.core.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ReflectionUtils.*;

/**
 * CommandTargetResolver that uses annotations on the command to identify the methods that provide the Aggregate
 * Identifier of the targeted Aggregate.
 * <p/>
 * This implementation expects at least one method (without parameters) or field in the command to be annotated with
 * {@link TargetAggregateIdentifier}. If on a method, the result of the invocation of that method will use as Aggregate
 * Identifier. If on a field, the value held in that field is used.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class AnnotationCommandTargetResolver implements CommandTargetResolver {

    private final Class<? extends Annotation> identifierAnnotation;

    /**
     * Instantiate a Builder to be able to create a {@code AnnotationCommandTargetResolver}.
     * <p>
     * The TargetAggregateIdentifierAnnotation is defaulted to {@link TargetAggregateIdentifier}.
     *
     * @return a Builder to be able to create a {@code AnnotationCommandTargetResolver}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@code AnnotationCommandTargetResolver} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@code AnnotationCommandTargetResolver} instance
     */
    protected AnnotationCommandTargetResolver(Builder builder) {
        this.identifierAnnotation = builder.identifierAnnotation;
    }

    @Override
    public String resolveTarget(@Nonnull CommandMessage command) {
        Object aggregateIdentifier;
        try {
            aggregateIdentifier = findIdentifier(command);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("An exception occurred while extracting aggregate "
                                                       + "information form a command", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("The current security context does not allow extraction of "
                                                       + "aggregate information from the given command.", e);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The value provided for the version is not a number.", e);
        }
        if (aggregateIdentifier == null) {
            throw new IllegalArgumentException(format(
                    "Invalid command. It does not identify the target aggregate. "
                            + "Make sure at least one of the fields or methods in the command of type [%s] contains the "
                            + "@TargetAggregateIdentifier annotation and that it returns a non-null value.",
                    command.type()
            ));
        }
        return aggregateIdentifier.toString();
    }

    private Object findIdentifier(Message command) throws InvocationTargetException, IllegalAccessException {
        return invokeAnnotated(command, identifierAnnotation);
    }

    @SuppressWarnings("deprecation") // Suppressed ReflectionUtils#ensureAccessible
    private static Object invokeAnnotated(
            Message command, Class<? extends Annotation> annotation
    ) throws InvocationTargetException, IllegalAccessException {
        for (Method m : methodsOf(command.payloadType())) {
            if (AnnotationUtils.isAnnotationPresent(m, annotation)) {
                ensureAccessible(m);
                return m.invoke(command.payload());
            }
        }
        for (Field f : fieldsOf(command.payloadType())) {
            if (AnnotationUtils.isAnnotationPresent(f, annotation)) {
                return getFieldValue(f, command.payload());
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "AnnotationCommandTargetResolver [identifierAnnotation=" + identifierAnnotation + "]";
    }

    /**
     * Builder class to instantiate a {@link AnnotationCommandTargetResolver}.
     * <p>
     * The TargetAggregateIdentifierAnnotation is defaulted to {@link TargetAggregateIdentifier}.
     *
     * @author JohT
     */
    public static final class Builder {

        private Class<? extends Annotation> identifierAnnotation = TargetAggregateIdentifier.class;

        /**
         * Sets the annotation, that marks the target aggregate identifier.
         * <p>
         * Defaults to {@link TargetAggregateIdentifier}.<br>
         * <p>
         * Use this method if you use another annotation to mark the field or method that identifies the target
         * aggregate, and it is not possible to put @{@link TargetAggregateIdentifier} into that annotation (to use it
         * as meta-annotation).
         *
         * @param annotation {@link Class} of type {@link Annotation}.
         * @return the current {@code Builder} instance, for fluent interfacing
         */
        public Builder targetAggregateIdentifierAnnotation(Class<? extends Annotation> annotation) {
            assertNonNull(annotation, "TargetAggregateIdentifierAnnotation may not be null");
            this.identifierAnnotation = annotation;
            return this;
        }

        /**
         * Initializes a {@link AnnotationCommandTargetResolver} as specified through this Builder.
         *
         * @return a {@link AnnotationCommandTargetResolver} as specified through this Builder
         */
        public AnnotationCommandTargetResolver build() {
            return new AnnotationCommandTargetResolver(this);
        }
    }
}
