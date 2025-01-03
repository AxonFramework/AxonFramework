/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.common;

import jakarta.annotation.Nonnull;

/**
 * Implementation that represents an empty Context.
 * @since 5.0.0
 * @author Allard Buijze
 */
class EmptyContext implements Context {

    /**
     * Returns the singleton instance of the empty context.
     */
    public static final EmptyContext INSTANCE = new EmptyContext();

    private EmptyContext() {
    }

    @Override
    public boolean containsResource(@Nonnull ResourceKey<?> key) {
        return false;
    }

    @Override
    public <T> T getResource(@Nonnull ResourceKey<T> key) {
        return null;
    }

    @Override
    public <T> Context withResource(@Nonnull ResourceKey<T> key, @Nonnull T resource) {
        return Context.with(key, resource);
    }
}
