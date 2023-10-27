/*
 * Copyright (c) 2010-2023. Axon Framework
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

/**
 * Interface that provides a mechanism to cancel a registration.
 *
 * @author Rene de Waele
 * @since 3.0
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

    /**
     * Cancels this Registration. By default this simply calls {@link #cancel()}.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    default void close() {
        cancel();
    }

    /**
     * Cancels this Registration. If the Registration was already cancelled, no action is taken.
     *
     * @return {@code true} if this handler is successfully deregistered, {@code false} if this handler
     * was not currently registered.
     */
    boolean cancel();
}
