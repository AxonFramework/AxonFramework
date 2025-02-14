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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotations.EventTag;

import java.lang.annotation.Annotation;
import java.util.Set;

public class AnnotationBasedTagResolver implements TagResolver {

    private static final Class<? extends Annotation> EVENT_TAG_ANNOTATION = EventTag.class;

    @Override
    public Set<Tag> resolve(@Nonnull EventMessage<?> event) {
        return Set.of();
    }
}
