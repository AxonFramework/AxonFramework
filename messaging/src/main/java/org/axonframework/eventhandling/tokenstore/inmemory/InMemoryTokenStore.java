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

package org.axonframework.eventhandling.tokenstore.inmemory;

import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of a {@link TokenStore} that stores tracking tokens in memory. This implementation is thread-safe.
 *
 * @author Rene de Waele
 * @author Christophe Bouhier
 */
public class InMemoryTokenStore implements TokenStore {

    private static final GlobalSequenceTrackingToken NULL_TOKEN = new GlobalSequenceTrackingToken(-1);

    private final Map<ProcessAndSegment, TrackingToken> tokens = new ConcurrentHashMap<>();

    @Override
    public void initializeTokenSegments(String processorName, int segmentCount) throws UnableToClaimTokenException {
        initializeTokenSegments(processorName, segmentCount, null);
    }

    @Override
    public void initializeTokenSegments(String processorName, int segmentCount, TrackingToken initialToken) throws UnableToClaimTokenException {
        if (fetchSegments(processorName).length > 0) {
            throw new UnableToClaimTokenException("Could not initialize segments. Some segments were already present.");
        }
        for (int segment = 0; segment < segmentCount; segment++) {
            tokens.put(new ProcessAndSegment(processorName, segment), getOrDefault(initialToken, NULL_TOKEN));
        }
    }

    @Override
    public void storeToken(TrackingToken token, String processorName, int segment) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().afterCommit(uow -> tokens.put(new ProcessAndSegment(processorName, segment), getOrDefault(token, NULL_TOKEN)));
        } else {
            tokens.put(new ProcessAndSegment(processorName, segment), getOrDefault(token, NULL_TOKEN));
        }
    }

    @Override
    public TrackingToken fetchToken(String processorName, int segment) {
        TrackingToken trackingToken = tokens.computeIfAbsent(new ProcessAndSegment(processorName, segment),
                                                             k -> NULL_TOKEN);
        if (NULL_TOKEN == trackingToken) {
            return null;
        }
        return trackingToken;
    }

    @Override
    public void releaseClaim(String processorName, int segment) {
        // no-op, the in-memory implementation isn't accessible by multiple processes
    }

    @Override
    public int[] fetchSegments(String processorName) {
        return tokens.keySet().stream()
                     .filter(ps -> ps.processorName.equals(processorName))
                     .map(ProcessAndSegment::getSegment)
                     .distinct().mapToInt(Number::intValue).toArray();
    }

    private static class ProcessAndSegment {

        private final String processorName;

        private final int segment;

        public ProcessAndSegment(String processorName, int segment) {
            this.processorName = processorName;
            this.segment = segment;
        }

        public int getSegment() {
            return segment;
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
