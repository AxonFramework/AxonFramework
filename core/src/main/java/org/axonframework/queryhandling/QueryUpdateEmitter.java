/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.queryhandling;

/**
 * Emitter used on query handling side in order to emit incremental updates on query side.
 *
 * @param <U> the type of incremental updates
 * @author Milan Savic
 * @see UpdateHandler
 * @since 3.3
 */
public interface QueryUpdateEmitter<U> {

    /**
     * Emits a single update.
     *
     * @param update the update
     * @return {@code true} if emit was successful
     */
    boolean emit(U update);

    /**
     * Informs query side that there are no more updates.
     */
    void complete();

    /**
     * Informs query side that error occurred.
     *
     * @param error the error
     */
    void error(Throwable error);

    /**
     * Registers a handler to be invoked when query cancels the registration.
     *
     * @param r the handler to be invoked
     */
    void onRegistrationCanceled(Runnable r);
}
