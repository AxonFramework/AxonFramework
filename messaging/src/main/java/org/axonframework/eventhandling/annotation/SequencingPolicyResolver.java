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
 * Interface for resolving sequencing policy annotations from event handler methods.
 * Implementations can handle different types of annotations (direct {@link SequencingPolicy}
 * annotations, meta-annotations, etc.) and convert them to a standard {@link SequencingPolicy}
 * representation.
 * <p>
 * This interface supports the Chain of Responsibility pattern, allowing multiple resolvers
 * to be chained together to handle different annotation types in a clean, extensible way.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public interface SequencingPolicyResolver {

    /**
     * Attempts to resolve a {@link SequencingPolicy} from the given method.
     * The resolver should examine the method and its declaring class for relevant
     * annotations and return a {@link SequencingPolicy} if one can be derived.
     *
     * @param method The method to examine for sequencing policy annotations
     * @return An {@link Optional} containing the resolved {@link SequencingPolicy}
     *         if this resolver can handle the annotations on the method, or
     *         {@link Optional#empty()} if this resolver cannot handle the method
     */
    Optional<SequencingPolicy> resolve(Method method);
}