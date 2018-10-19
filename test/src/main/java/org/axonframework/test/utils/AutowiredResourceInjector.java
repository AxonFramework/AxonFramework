/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.test.utils;

import org.axonframework.modelling.saga.AbstractResourceInjector;

import java.util.Optional;

/**
 * Resource injector that uses setter methods to inject resources. All methods and fields annotated with
 * {@code @Inject} are evaluated. If that method has a single parameter, a Resource of that type
 * is injected into it, if present.
 * <p>
 * Unlike the SimpleResourceInjector, changes in the provided {@link Iterable} are reflected in this injector.
 *
 * @author Allard Buijze
 * @since 1.1
 */
public class AutowiredResourceInjector extends AbstractResourceInjector {

    private final Iterable<?> resources;

    /**
     * Initializes the resource injector to inject to given {@code resources}.
     *
     * @param resources The resources to inject
     */
    public AutowiredResourceInjector(Iterable<?> resources) {
        this.resources = resources;
    }

    @Override
    protected <R> Optional<R> findResource(Class<R> requiredType) {
        for (Object resource : resources) {
            if (requiredType.isInstance(resource)) {
                return Optional.of(requiredType.cast(resource));
            }
        }
        return Optional.empty();
    }

}
