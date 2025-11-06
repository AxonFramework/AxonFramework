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

import java.util.concurrent.Executor;

/**
 * Simple executor implementation that runs a given Runnable immediately in the calling thread.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public final class DirectExecutor implements Executor {

    private DirectExecutor() {
    }

    /**
     * Returns a singleton instance of the DirectExecutor. Using this constant prevents the creation of unnecessary
     * DirectExecutor instances.
     */
    public static final DirectExecutor INSTANCE = new DirectExecutor();

    /**
     * Returns the (singleton) instance of the DirectExecutor
     *
     * @return the one and only DirectExecutor
     */
    public static DirectExecutor instance() {
        return INSTANCE;
    }

    /**
     * Executes the given {@code command} immediately in the current thread.
     *
     * @param command the command to execute.
     */
    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
