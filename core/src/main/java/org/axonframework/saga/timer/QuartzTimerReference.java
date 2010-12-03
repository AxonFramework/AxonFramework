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

package org.axonframework.saga.timer;

/**
 * @author Allard Buijze
 * @since 0.7
 */
class QuartzTimerReference implements TimerReference {

    private static final long serialVersionUID = 7798276124742118925L;

    private final String taskIdentifier;
    private final String sagaIdentifier;

    public QuartzTimerReference(String taskIdentifier, String sagaIdentifier) {
        this.taskIdentifier = taskIdentifier;
        this.sagaIdentifier = sagaIdentifier;
    }

    @Override
    public String getIdentifier() {
        return taskIdentifier;
    }

    @Override
    public String getSagaIdentifier() {
        return sagaIdentifier;
    }
}
