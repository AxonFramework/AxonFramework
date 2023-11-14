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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ReflectionUtils.*;

/**
 * CommandTargetResolver that uses annotations on the command to identify the methods that provide the Aggregate
 * Identifier of the targeted Aggregate and optionally the expected version of the aggregate.
 * <p/>
 * This implementation expects at least one method (without parameters) or field in the command to be annotated with
 * {@link TargetAggregateIdentifier}. If on a method, the result of the invocation of that method will use as Aggregate
 * Identifier. If on a field, the value held in that field is used.
 * <p/>
 * Similarly, the expected aggregate version may be provided by annotating a method (without parameters) or field with
 * {@link TargetAggregateVersion}. The return value of the method or value held in the field is used as the expected
 * version. Note that the method must return a Long value, or a value that may be parsed as a Long.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class AnnotationCommandTargetResolver implements CommandTargetResolver {

    private final Class<? extends Annotation> identifierAnnotation;
    private final Class<? extends Annotation> versionAnnotation;

    /**
     * Instantiate a Builder to be able to create a {@link AnnotationCommandTargetResolver}.
     * <p>
     * The TargetAggregateIdentifierAnnotation is defaulted to {@link TargetAggregateIdentifier},
     * TargetAggregateVersionAnnotation to {@link TargetAggregateVersion}.
     *
     * @return a Builder to be able to create a{@link AnnotationCommandTargetResolver}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link AnnotationCommandTargetResolver} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AnnotationCommandTargetResolver} instance
     */
    protected AnnotationCommandTargetResolver(Builder builder) {
        this.identifierAnnotation = builder.identifierAnnotation;
        this.versionAnnotation = builder.versionAnnotation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VersionedAggregateIdentifier resolveTarget(@Nonnull CommandMessage<?> command) {
        Long aggregateVersion;
        Object aggregateIdentifier;
        try {
            aggregateIdentifier = findIdentifier(command);
            aggregateVersion = findVersion(command);
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
                            + "Make sure at least one of the fields or methods in the [%s] class contains the "
                            + "@TargetAggregateIdentifier annotation and that it returns a non-null value.",
                    command.getPayloadType().getSimpleName()
            ));
        }
        return new VersionedAggregateIdentifier(aggregateIdentifier, aggregateVersion);
    }

    private Object findIdentifier(Message<?> command) throws InvocationTargetException, IllegalAccessException {
        return invokeAnnotated(command, identifierAnnotation);
    }

    private Long findVersion(Message<?> command) throws InvocationTargetException, IllegalAccessException {
        return asLong(invokeAnnotated(command, versionAnnotation));
    }

    private static Object invokeAnnotated(
            Message<?> command, Class<? extends Annotation> annotation
    ) throws InvocationTargetException, IllegalAccessException {
        for (Method m : methodsOf(command.getPayloadType())) {
            if (AnnotationUtils.isAnnotationPresent(m, annotation)) {
                ensureAccessible(m);
                return m.invoke(command.getPayload());
            }
        }
        for (Field f : fieldsOf(command.getPayloadType())) {
            if (AnnotationUtils.isAnnotationPresent(f, annotation)) {
                return getFieldValue(f, command.getPayload());
            }
        }
        return null;
    }

    private Long asLong(Object fieldValue) {
        if (fieldValue == null) {
            return null;
        } else if (fieldValue instanceof Number) {
            return ((Number) fieldValue).longValue();
        } else {
            return Long.parseLong(fieldValue.toString());
        }
    }

    @Override
    public String toString() {
        return "AnnotationCommandTargetResolver [identifierAnnotation="
                + identifierAnnotation + ", versionAnnotation="
                + versionAnnotation + "]";
    }

    /**
     * Builder class to instantiate a {@link AnnotationCommandTargetResolver}.
     * <p>
     * The TargetAggregateIdentifierAnnotation is defaulted to {@link TargetAggregateIdentifier},
     * TargetAggregateVersionAnnotation to {@link TargetAggregateVersion}.
     *
     * @author JohT
     */
    public static final class Builder {

        private Class<? extends Annotation> identifierAnnotation = TargetAggregateIdentifier.class;
        private Class<? extends Annotation> versionAnnotation = TargetAggregateVersion.class;

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
         * @return the current {@link Builder} instance, for fluent interfacing
         */
        public Builder targetAggregateIdentifierAnnotation(Class<? extends Annotation> annotation) {
            assertNonNull(annotation, "TargetAggregateIdentifierAnnotation may not be null");
            this.identifierAnnotation = annotation;
            return this;
        }

        /**
         * Sets the annotation, that marks the target aggregate version.
         * <p>
         * Defaults to {@link TargetAggregateVersion}.
         * <p>
         * Use this method if you use another annotation to mark the field or method that identifies the version of the
         * aggregate, and it is not possible to put @{@link TargetAggregateVersion} into that annotation (to use it as
         * meta-annotation).
         *
         * @param annotation {@link Class} of type {@link Annotation}.
         * @return the current {@link Builder} instance, for fluent interfacing
         */
        public Builder targetAggregateVersionAnnotation(Class<? extends Annotation> annotation) {
            assertNonNull(annotation, "TargetAggregateVersionAnnotation may not be null");
            this.versionAnnotation = annotation;
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
