/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.*;

/**
 * CommandTargetResolver that uses annotations on the command to identify the methods that provide the
 * Aggregate Identifier of the targeted Aggregate and optionally the expected version of the aggregate.
 * <p/>
 * This implementation expects at least one method (without parameters) or field in the command to be annotated with
 * {@link TargetAggregateIdentifier}. If on a method, the result of the invocation of that method will used as
 * Aggregate Identifier. If on a field, the value held in that field is used.
 * <p/>
 * Similarly, the expected aggregate version may be provided by annotating a method (without parameters) or field with
 * {@link TargetAggregateVersion}. The return value of the method or value held in the field is used as the expected
 * version. Note that the method must return a Long value, or a value that may be parsed as a Long.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class AnnotationCommandTargetResolver implements CommandTargetResolver {

    @Override
    public VersionedAggregateIdentifier resolveTarget(CommandMessage<?> command) {
        String aggregateIdentifier;
        Long aggregateVersion;
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
            throw new IllegalArgumentException(
                    format("Invalid command. It does not identify the target aggregate. "
                                   + "Make sure at least one of the fields or methods in the [%s] class contains the "
                                   + "@TargetAggregateIdentifier annotation and that it returns a non-null value.",
                           command.getPayloadType().getSimpleName()));
        }
        return new VersionedAggregateIdentifier(aggregateIdentifier, aggregateVersion);
    }

    private String findIdentifier(CommandMessage<?> command)
            throws InvocationTargetException, IllegalAccessException {
        for (Method m : methodsOf(command.getPayloadType())) {
            if (m.isAnnotationPresent(TargetAggregateIdentifier.class)) {
                ensureAccessible(m);
                return Optional.ofNullable(m.invoke(command.getPayload())).map(Object::toString).orElse(null);
            }
        }
        for (Field f : fieldsOf(command.getPayloadType())) {
            if (f.isAnnotationPresent(TargetAggregateIdentifier.class)) {
                return Optional.ofNullable(getFieldValue(f, command.getPayload())).map(Object::toString).orElse(null);
            }
        }
        return null;
    }

    private Long findVersion(CommandMessage<?> command) throws InvocationTargetException, IllegalAccessException {
        for (Method m : methodsOf(command.getPayloadType())) {
            if (m.isAnnotationPresent(TargetAggregateVersion.class)) {
                ensureAccessible(m);
                return asLong(m.invoke(command.getPayload()));
            }
        }
        for (Field f : fieldsOf(command.getPayloadType())) {
            if (f.isAnnotationPresent(TargetAggregateVersion.class)) {
                return asLong(getFieldValue(f, command.getPayload()));
            }
        }
        return null;
    }

    private Long asLong(Object fieldValue) {
        if (fieldValue == null) {
            return null;
        } else if (Number.class.isInstance(fieldValue)) {
            return ((Number) fieldValue).longValue();
        } else {
            return Long.parseLong(fieldValue.toString());
        }
    }
}
