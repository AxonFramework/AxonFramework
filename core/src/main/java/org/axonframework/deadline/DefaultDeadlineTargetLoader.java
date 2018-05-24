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

package org.axonframework.deadline;

import org.axonframework.commandhandling.model.RepositoryProvider;
import org.axonframework.eventhandling.saga.SagaRepositoryProvider;

/**
 * Default implementation of {@link DeadlineTargetLoader}. It uses {@link RepositoryProvider} and {@link
 * SagaRepositoryProvider} in order to load the target deadline handlers.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class DefaultDeadlineTargetLoader implements DeadlineTargetLoader {

    private final RepositoryProvider repositoryProvider;
    private final SagaRepositoryProvider sagaRepositoryProvider;

    /**
     * Initializes default deadline target loader.
     *
     * @param repositoryProvider     Responsible for providing correct aggregate repository
     * @param sagaRepositoryProvider Responsible for providing correct saga repository
     */
    public DefaultDeadlineTargetLoader(RepositoryProvider repositoryProvider,
                                       SagaRepositoryProvider sagaRepositoryProvider) {
        this.repositoryProvider = repositoryProvider;
        this.sagaRepositoryProvider = sagaRepositoryProvider;
    }

    @Override
    public DeadlineAware load(DeadlineContext deadlineContext) {
        switch (deadlineContext.getType()) {
            case SAGA:
                return sagaRepositoryProvider
                        .repositoryFor(deadlineContext.getTargetType())
                        .load(deadlineContext.getId());
            case AGGREGATE:
                return repositoryProvider
                        .repositoryFor(deadlineContext.getTargetType())
                        .load(deadlineContext.getId());
            default:
                throw new IllegalStateException(
                        "Unsupported deadline context type: '" + deadlineContext.getType() + "'");
        }
    }
}
