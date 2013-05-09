/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.replay;

/**
 * Interface indicating a component is aware of "replays". Typically, these will be Event Listeners that can rebuild
 * their state based on Events stored in the Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface ReplayAware {

    /**
     * Invoked when a replay is started.
     */
    void beforeReplay();

    /**
     * Invoked when a replay has finished.
     */
    void afterReplay();

    /**
     * Invoked when a replay has failed due to an exception.
     *
     * @param cause The exception that stopped the replay;
     */
    void onReplayFailed(Throwable cause);
}
