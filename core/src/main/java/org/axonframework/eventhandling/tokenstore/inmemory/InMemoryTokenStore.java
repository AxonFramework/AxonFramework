/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.inmemory;

import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a {@link TokenStore} that stores tracking tokens in memory. This implementation is thread-safe.
 *
 * @author Rene de Waele
 */
public class InMemoryTokenStore implements TokenStore {
    private final Map<ProcessAndSegment, TrackingToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void storeToken(TrackingToken token, String processorName, int segment) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().afterCommit(uow -> tokens.put(new ProcessAndSegment(processorName, segment), token));
        } else {
            tokens.put(new ProcessAndSegment(processorName, segment), token);
        }
    }

    @Override
    public TrackingToken fetchToken(String processorName, int segment) {
        return tokens.get(new ProcessAndSegment(processorName, segment));
    }

    @Override
    public void releaseClaim(String processorName, int segment) {
        // no-op, the in-memory implementation isn't accessible by multiple processes
    }

    private static class ProcessAndSegment {
        private final String processorName;
        private final int segment;

        public ProcessAndSegment(String processorName, int segment) {
            this.processorName = processorName;
            this.segment = segment;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProcessAndSegment that = (ProcessAndSegment) o;
            return segment == that.segment && Objects.equals(processorName, that.processorName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(processorName, segment);
        }
    }
}
