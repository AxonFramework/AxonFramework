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

package org.axonframework.eventhandling.annotation;

import org.axonframework.eventhandling.annotations.SequencingByProperty;
import org.axonframework.eventhandling.annotations.SequencingPolicy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@link SequencingPolicyResolver} implementation that handles {@link SequencingByProperty}
 * meta-annotations on methods or their declaring classes.
 * <p>
 * This resolver looks for {@link SequencingByProperty} annotations in the following order:
 * <ol>
 *   <li>Method-level {@link SequencingByProperty} annotation</li>
 *   <li>Class-level {@link SequencingByProperty} annotation</li>
 * </ol>
 * <p>
 * When found, the {@link SequencingByProperty} annotation is converted to an equivalent
 * {@link SequencingPolicy} annotation with the property name as the parameter.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SequencingByPropertyResolver implements SequencingPolicyResolver {

    @Override
    public Optional<SequencingPolicy> resolve(Method method) {
        return Optional.ofNullable(method.getAnnotation(SequencingByProperty.class))
                       .or(() -> Optional.ofNullable(method.getDeclaringClass().getAnnotation(SequencingByProperty.class)))
                       .map(this::convertToSequencingPolicy);
    }

    /**
     * Converts a {@link SequencingByProperty} annotation to an equivalent {@link SequencingPolicy} annotation.
     *
     * @param annotation The {@link SequencingByProperty} annotation to convert
     * @return An equivalent {@link SequencingPolicy} annotation
     */
    private SequencingPolicy convertToSequencingPolicy(SequencingByProperty annotation) {
        return new SequencingPolicy() {
            @Override
            public Class<? extends org.axonframework.eventhandling.sequencing.SequencingPolicy> type() {
                return annotation.annotationType().getAnnotation(SequencingPolicy.class).type();
            }

            @Override
            public String[] parameters() {
                return new String[]{annotation.value()};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return SequencingPolicy.class;
            }
        };
    }
}