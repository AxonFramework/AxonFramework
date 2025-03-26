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

package org.axonframework.eventsourcing.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ConstructorBasedEventSourcedEntityCreator<ID, M> implements EventSourcedEntityCreator<ID, M>, DescribableComponent {

    private final Class<M> entityType;
    private final Map<Class<ID>, Function<ID, M>> constructorCache = new ConcurrentHashMap<>();

    public ConstructorBasedEventSourcedEntityCreator(Class<M> entityType) {
        this.entityType = entityType;
    }

    @Override
    public M createEntity(Class<M> entityType, ID id) {
        return ReflectionUtils.constructWithOptionalArguments(entityType, id);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType.getName());
        descriptor.describeProperty("constructorCache", constructorCache);
    }
}
