/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.util;

import java.util.concurrent.Executor;

/**
 * Simple executor implementation that runs a given Runnable immediately in the calling thread.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public final class SynchronousTaskExecutor implements Executor {

    /**
     * Returns a singleton instance of the SynchronousTaskExecutor.
     */
    public static final SynchronousTaskExecutor INSTANCE = new SynchronousTaskExecutor();

    private SynchronousTaskExecutor() {
    }

    /**
     * Executes the given <code>command</code> immediately in the current thread.
     *
     * @param command the command to execute.
     */
    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
