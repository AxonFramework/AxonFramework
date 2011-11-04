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

package org.axonframework.saga.annotation;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the election of which asynchronous saga event processor is responsible for creating a new Saga instance,
 * when necessary.
 *
 * @author Allard Buijze
 * @since 2.0
 */
class AsyncSagaCreationElector {

    private final ReentrantLock votingLock = new ReentrantLock();
    private final Condition allVotesCast = votingLock.newCondition();

    // guarded by "votingLock"
    private int castVotes = 0;
    private volatile boolean invocationDetected = false;

    /**
     * Forces the current thread to wait for the voting to complete if it is responsible for creating the Saga. As soon
     * as an invocation has been recorded, the waiting thread is released.
     *
     * @param didInvocation      indicates whether the current processor found a Saga to process
     * @param totalVotesExpected The total number of processors expected to cast a vote
     * @param isSagaOwner        Indicates whether the current processor "owns" the to-be-created saga instance.
     * @return <code>true</code> if the current processor should create the new instance, <code>false</code> otherwise.
     */
    public boolean waitForSagaCreationVote(final boolean didInvocation, final int totalVotesExpected,
                                           final boolean isSagaOwner) {
        votingLock.lock();
        try {
            invocationDetected = invocationDetected || didInvocation;
            castVotes++;
            while (isSagaOwner && !invocationDetected && castVotes < totalVotesExpected) {
                try {
                    allVotesCast.await();
                } catch (InterruptedException e) {
                    // interrupting this process is not supported.
                }
            }
            if (isSagaOwner) {
                return !invocationDetected;
            }
            allVotesCast.signalAll();
        } finally {
            votingLock.unlock();
        }
        return false;
    }

    /**
     * Clears the voting counts for a new round.
     */
    public void clear() {
        votingLock.lock();
        try {
            castVotes = 0;
            invocationDetected = false;
        } finally {
            votingLock.unlock();
        }
    }
}
