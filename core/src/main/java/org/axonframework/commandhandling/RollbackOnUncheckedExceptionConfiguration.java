/*
 * Copyright (c) 2010-2011. Axon Framework
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
 * RollbackConfiguration that indicates a rollback should be triggered for all but checked exceptions. Therefore,
 * Errors and RuntimeExceptions will cause a rollback.
 *
 * @author Martin Tilma
 * @since 1.1
 */
public class RollbackOnUncheckedExceptionConfiguration implements RollbackConfiguration {

    @Override
    public boolean rollBackOn(Throwable throwable) {
        return !(throwable instanceof Exception) || throwable instanceof RuntimeException;
    }
}
