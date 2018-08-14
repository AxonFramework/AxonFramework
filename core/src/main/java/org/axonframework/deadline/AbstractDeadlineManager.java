/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.deadline;

import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * Abstract implementation of the {@link DeadlineManager} to be implemented by concrete solutions for the
 * DeadlineManager. Provides functionality to perform a call to the DeadlineManager in the
 * {@link UnitOfWork.Phase#PREPARE_COMMIT} phase. This #runOnPrepareCommitOrNow(Runnable) functionality is required, as
 * the DeadlineManager schedules a {@link org.axonframework.messaging.Message} which needs to happen on order with the
 * other messages published throughout the system.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public abstract class AbstractDeadlineManager implements DeadlineManager {

    /**
     * Run a given {@code deadlineCall} immediately, or schedule it for the {@link UnitOfWork.Phase#PREPARE_COMMIT}
     * phase if a UnitOfWork is active. This is required as the DeadlineManager schedule message which we want to happen
     * on order with other message being handled.
     *
     * @param deadlineCall a {@link Runnable} to be executed now or on prepare commit if a {@link UnitOfWork} is active
     */
    protected void runOnPrepareCommitOrNow(Runnable deadlineCall) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().onPrepareCommit(unitOfWork -> deadlineCall.run());
        } else {
            deadlineCall.run();
        }
    }
}
