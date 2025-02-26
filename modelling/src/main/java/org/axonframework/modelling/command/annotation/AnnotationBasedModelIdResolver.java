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

import jakarta.annotation.Nonnull;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.ModelIdResolver;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * Implementation of a {@link ModelIdResolver} that inspects the payload of a {@link Message} for an identifier. The
 * identifier is resolved by looking for a field or method annotated with {@link TargetModelIdentifier}.
 * <p>
 * If multiple identifiers are found, an {@link MultipleIdentifiersInPayloadException} is thrown. It is advised to
 * implement your own {@link ModelIdResolver} if a compound identifier is expected, or to make a getter method that
 * returns the compound identifier.
 * <p>
 * If no identifier is found, {@code null} is returned. This indicates that either no field has been found, or that the
 * field has a {@code null} value.
 * <p>
 * For performance reasons, the resolved identifier members are cached per payload type.
 *
 * @author Mitchell Herrijgers
 * @see TargetModelIdentifier
 * @see ModelIdResolver
 * @since 5.0.0
 */
public class AnnotationBasedModelIdResolver implements ModelIdResolver<Object> {

    private static final Class<TargetModelIdentifier> IDENTIFIER_ANNOTATION = TargetModelIdentifier.class;
    private final Map<Class<?>, List<Member>> cache = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Object resolve(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        Object payload = message.getPayload();
        List<Object> identifiers = getIdentifiers(payload);
        if (identifiers.size() > 1) {
            throw new MultipleIdentifiersInPayloadException(identifiers, payload.getClass());
        }
        if (identifiers.isEmpty()) {
            return null;
        }
        return identifiers.getFirst();
    }

    /**
     * Extracts the identifiers from the payload by looking for fields or methods annotated with
     * {@link TargetModelIdentifier}.
     *
     * @param payload The payload to extract the identifiers from
     * @return The identifiers found in the payload
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
     * Retrieves the members annotated with {@link TargetModelIdentifier} from the given {@code type}. If not present in
     * the cache, the members are retrieved and cached.
     *
     * @param type The type to retrieve the members from
     * @return The members annotated with {@link TargetModelIdentifier}
     */
    private List<Member> getMembers(Class<?> type) {
        return cache.computeIfAbsent(type, this::findMembers);
    }

    /**
     * Finds the members annotated with {@link TargetModelIdentifier} in the given {@code type}.
     *
     * @param type The type to find the members in
     * @return The members annotated with {@link TargetModelIdentifier}
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
     * Checks if the given {@code member} is annotated with {@link TargetModelIdentifier}.
     * @param member The member to check for the annotation
     * @return {@code true} if the member is annotated with {@link TargetModelIdentifier}, {@code false} otherwise
     */
    private boolean hasIdentifierAnnotation(AnnotatedElement member) {
        return member.isAnnotationPresent(IDENTIFIER_ANNOTATION);
    }
}