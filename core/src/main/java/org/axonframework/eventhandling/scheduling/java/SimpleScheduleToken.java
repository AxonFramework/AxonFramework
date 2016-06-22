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

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.eventhandling.scheduling.ScheduleToken;

/**
 * ScheduleToken for tasks event scheduled using the SimpleEventScheduler.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SimpleScheduleToken implements ScheduleToken {

    private static final long serialVersionUID = -8118223354702247016L;
    private final String tokenId;

    /**
     * Creates a SimpleScheduleToken with the given {@code tokenId}.
     *
     * @param tokenId The identifier referencing the scheduled task.
     */
    public SimpleScheduleToken(String tokenId) {
        this.tokenId = tokenId;
    }

    /**
     * Returns the identifier of the scheduled task.
     *
     * @return the identifier of the scheduled task
     */
    public String getTokenId() {
        return tokenId;
    }
}
