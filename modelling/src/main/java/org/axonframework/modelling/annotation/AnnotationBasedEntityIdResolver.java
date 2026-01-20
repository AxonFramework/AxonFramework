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

import jakarta.annotation.Nonnull;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityIdResolutionException;
import org.axonframework.modelling.EntityIdResolver;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of a {@link EntityIdResolver} that inspects the payload of a {@link Message} for fields or methods
 * annotated with {@link TargetEntityId}.
 * <p>
 * Multiple fields may be annotated, but only exactly one must resolve one distinct non-null value.
 * <p>
 * If no ids or multiple ids are found, an {@link EntityIdResolutionException} is thrown.
 * <p>
 * For performance reasons, the resolved identifier members are cached per payload type.
 *
 * @param <T> The type of the identifier to resolve.
 * @author Mitchell Herrijgers
 * @see TargetEntityId
 * @see EntityIdResolver
 * @since 5.0.0
 */
public class AnnotationBasedEntityIdResolver<T> implements EntityIdResolver<T> {

    private static final Class<TargetEntityId> IDENTIFIER_ANNOTATION = TargetEntityId.class;
    private final Map<Class<?>, List<Member>> cache = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public T resolve(@Nonnull Message message, @Nonnull ProcessingContext context) throws EntityIdResolutionException {
        Object payload = message.payload();
        List<Object> identifiers = getIdentifiers(payload)
                .stream()
                .filter(Objects::nonNull)
                .toList();

        if (identifiers.size() == 1) {
            @SuppressWarnings("unchecked")
            T first = (T) identifiers.getFirst();

            return first;
        }

        throw new EntityIdResolutionException(payload.getClass(), identifiers);
    }

    /**
     * Extracts the ids from the payload by looking for fields and methods annotated with {@link TargetEntityId}.
     *
     * @param payload The payload to extract the identifiers from.
     * @return The identifiers found in the payload.
     */
    private List<Object> getIdentifiers(Object payload) {
        List<Member> members = getMembers(payload.getClass());
        return members
                .stream()
                .map(field -> ReflectionUtils.getMemberValue(field, payload))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Retrieves the members annotated with {@link TargetEntityId} from the given {@code type}. If not present in the
     * cache, the members are retrieved and cached.
     *
     * @param type The type to retrieve the members from.
     * @return The members annotated with {@link TargetEntityId}.
     */
    private List<Member> getMembers(Class<?> type) {
        return cache.computeIfAbsent(type, this::findMembers);
    }

    /**
     * Finds the members annotated with {@link TargetEntityId} in the given {@code type}.
     *
     * @param type The type to find the members in.
     * @return The members annotated with {@link TargetEntityId}.
     */
    private List<Member> findMembers(Class<?> type) {
        var fields = StreamSupport.stream(ReflectionUtils.fieldsOf(type).spliterator(), false)
                                  .filter(this::hasIdentifierAnnotation);
        var methods = StreamSupport.stream(ReflectionUtils.methodsOf(type).spliterator(), false)
                                   .filter(this::hasIdentifierAnnotation);
        return Stream.concat(fields, methods)
                     .map(Member.class::cast)
                     .toList();
    }

    /**
     * Checks if the given {@code member} is annotated with {@link TargetEntityId}.
     *
     * @param member The member to check for the annotation.
     * @return {@code true} if the member is annotated with {@link TargetEntityId}, {@code false} otherwise.
     */
    private boolean hasIdentifierAnnotation(AnnotatedElement member) {
        return member.isAnnotationPresent(IDENTIFIER_ANNOTATION);
    }
}