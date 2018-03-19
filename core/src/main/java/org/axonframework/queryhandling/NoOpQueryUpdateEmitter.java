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
 * Query update emitter implementation with empty methods.
 *
 * @param <U> the type of incremental updates
 * @author Milan Savic
 * @since 3.3
 */
public class NoOpQueryUpdateEmitter<U> implements QueryUpdateEmitter<U> {

    @Override
    public boolean emit(U update) {
        // this is empty implementation, since regular query handler will not invoke it
        return false;
    }

    @Override
    public void complete() {
        // this is empty implementation, since regular query handler will not invoke it
    }

    @Override
    public void error(Throwable error) {
        // this is empty implementation, since regular query handler will not invoke it
    }

    @Override
    public void onRegistrationCanceled(Runnable r) {
        r.run();
    }
}
