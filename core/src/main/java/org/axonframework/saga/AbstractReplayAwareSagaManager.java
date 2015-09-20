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

package org.axonframework.saga;

import org.axonframework.eventhandling.replay.EventReplayUnsupportedException;
import org.axonframework.eventhandling.replay.ReplayAware;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for dealing with event replays.
 *
 * @author Rene de Waele
 * @since 2.4.3
 */
public abstract class AbstractReplayAwareSagaManager implements SagaManager, ReplayAware {

    private volatile boolean replayable;

    /**
     * Sets whether or not to allow event replay on managed Sagas. If set to <code>false</code> the saga manager will
     * throw an {@link IllegalStateException} before a replay is started. Defaults to <code>false</code>.
     *
     * @param replayable whether or not to allow event replays on managed Sagas.
     */
    public void setReplayable(boolean replayable) {
        this.replayable = replayable;
    }

    @Override
    public void beforeReplay() {
        if (!replayable) {
            throw new EventReplayUnsupportedException("This Saga Manager does not support event replays. " +
                                                      "Replaying events on its Sagas may cause data corruption.");
        }
    }

    @Override
    public void afterReplay() {
    }

    @Override
    public void onReplayFailed(Throwable cause) {
    }
}
