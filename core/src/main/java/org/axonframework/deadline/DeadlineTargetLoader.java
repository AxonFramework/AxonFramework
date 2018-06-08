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

import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;

/**
 * Functional interface responsible for loading the final target which should handle the {@link DeadlineMessage} (if
 * deadline was not met). It uses {@link ScopeDescriptor} in order to locate the target.
 *
 * @author Milan Savic
 * @since 3.3
 */
@FunctionalInterface
public interface DeadlineTargetLoader {

    /**
     * Loads the target which is responsible (and capable) of handling the deadline if it was not met.
     *
     * @param deadlineScope The context in which deadline was scheduled
     * @return the target component to handle the {@link DeadlineMessage}
     */
    ScopeAware load(ScopeDescriptor deadlineScope);
}
