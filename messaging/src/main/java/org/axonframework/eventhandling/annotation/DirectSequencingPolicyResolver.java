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

import org.axonframework.eventhandling.annotations.SequencingPolicy;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * {@link SequencingPolicyResolver} implementation that handles direct {@link SequencingPolicy}
 * annotations on methods or their declaring classes.
 * <p>
 * This resolver looks for {@link SequencingPolicy} annotations in the following order:
 * <ol>
 *   <li>Method-level {@link SequencingPolicy} annotation</li>
 *   <li>Class-level {@link SequencingPolicy} annotation</li>
 * </ol>
 * <p>
 * Method-level annotations take precedence over class-level annotations.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class DirectSequencingPolicyResolver implements SequencingPolicyResolver {

    @Override
    public Optional<SequencingPolicy> resolve(Method method) {
        return Optional.ofNullable(method.getAnnotation(SequencingPolicy.class))
                       .or(() -> Optional.ofNullable(method.getDeclaringClass().getAnnotation(SequencingPolicy.class)));
    }
}