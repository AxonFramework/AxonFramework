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
 * Defines update handler callbacks for subscription query. The guarantee which specific implementation of query bus
 * should provide is that {@link UpdateHandler#onInitialResult(Object)} is always invoked before any other method in
 * this interface. However, there are no guarantees for other ordering of methods within this interface.
 *
 * @param <I> the type of initial result
 * @param <U> the type of incremental updates
 * @author Allard Buijze
 * @since 3.3
 */
public interface UpdateHandler<I, U> {

    /**
     * Will be invoked when query handler process the query request.
     *
     * @param initial the initial response
     */
    void onInitialResult(I initial);

    /**
     * Will be invoked when query handler emits the update on queried topic.
     *
     * @param update the incremental update
     */
    void onUpdate(U update);

    /**
     * Will be invoked when there are no more updates on queried topic.
     */
    void onCompleted();

    /**
     * Will be invoked when there is an error in query processing or when emitter invokes it explicitly.
     *
     * @param error the error which occurred
     */
    void onError(Throwable error);
}
