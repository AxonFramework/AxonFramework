/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling;

/**
 * The RollbackConfiguration defines when a UnitOfWork should be rolled back when the command handler or any
 * interceptors
 * reported a failure. This decision will only impact the UnitOfWork (and potentially any transactions attached to it),
 * the {@link CommandCallback#onFailure(CommandMessage, Throwable)} will always be called.
 *
 * @author Martin Tilma
 * @since 1.1
 */
public interface RollbackConfiguration {

    /**
     * Decides whether the given <code>throwable</code> should trigger a rollback.
     *
     * @param throwable the Throwable to evaluate
     * @return <code>true</code> if the UnitOfWork should be rolled back, otherwise <code>false</code>
     */
    boolean rollBackOn(Throwable throwable);
}
