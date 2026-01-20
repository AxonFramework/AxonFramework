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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * An {@link ApplicationContext} implementation that does not provide any components.
 * <p>
 * It is useful as a placeholder in tests, but you should never use it if you want to be able to retrieve components
 * from the {@link ProcessingContext} in your components.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class EmptyApplicationContext implements ApplicationContext {

    /**
     * Returns the singleton instance of the empty application context.
     */
    public static final EmptyApplicationContext INSTANCE = new EmptyApplicationContext();

    private EmptyApplicationContext() {
    }

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type, @Nullable String name) {
        throw new UnsupportedOperationException(
                "EmptyApplicationContext does not provide any components. " +
                        "You should never use it if you want to be able to retrieve components from the ProcessingContext."
        );
    }
}
