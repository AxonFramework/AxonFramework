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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentNotFoundException;

import java.util.Optional;

/**
 * An {@link ApplicationContext} implementation that does not provide any components.
 * It is useful as a placeholder or default context when no components are available.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class EmptyApplicationContext implements ApplicationContext {

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type, @Nullable String name) {
        throw new ComponentNotFoundException(type, name);
    }
}
