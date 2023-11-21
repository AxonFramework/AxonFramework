/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventhandling.tokenstore.UnableToInitializeTokenException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of a {@link TokenStore} that stores tracking tokens in memory. This implementation is thread-safe.
 *
 * @author Rene de Waele
 * @author Christophe Bouhier
 */
public class InMemoryTokenStore implements TokenStore {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final GlobalSequenceTrackingToken NULL_TOKEN = new GlobalSequenceTrackingToken(-1);

    private final Map<ProcessAndSegment, TrackingToken> tokens = new ConcurrentHashMap<>();
    private final String identifier = UUID.randomUUID().toString();

    /**
     * No-arg constructor for the {@link InMemoryTokenStore} which will log a warning on initialization.
     */
    public InMemoryTokenStore() {
        logger.warn("An in memory token store is being created.\nThis means the event processor using this token store might process the same events again when the application is restarted.\nIf the use of an in memory token store is intentional, this warning can be ignored.\nIf the tokens should be persisted, use the JPA, JDBC or MongoDB token store instead.");
    }

    @Override
    public void initializeTokenSegments(@Nonnull String processorName, int segmentCount)
            throws UnableToClaimTokenException {
        initializeTokenSegments(processorName, segmentCount, null);
    }

    @Override
    public void initializeTokenSegments(@Nonnull String processorName, int segmentCount, TrackingToken initialToken)
            throws UnableToClaimTokenException {
        if (fetchSegments(processorName).length > 0) {
            throw new UnableToClaimTokenException("Could not initialize segments. Some segments were already present.");
        }
        for (int segment = 0; segment < segmentCount; segment++) {
            tokens.put(new ProcessAndSegment(processorName, segment), getOrDefault(initialToken, NULL_TOKEN));
        }
    }

    @Override
    public void storeToken(TrackingToken token, @Nonnull String processorName, int segment) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().afterCommit(uow -> tokens.put(new ProcessAndSegment(processorName, segment),
                                                                  getOrDefault(token, NULL_TOKEN)));
        } else {
            tokens.put(new ProcessAndSegment(processorName, segment), getOrDefault(token, NULL_TOKEN));
        }
    }

    @Override
    public TrackingToken fetchToken(@Nonnull String processorName, int segment) {
        TrackingToken trackingToken = tokens.get(new ProcessAndSegment(processorName, segment));
        if (trackingToken == null) {
            throw new UnableToClaimTokenException(
                    "No token was initialized for segment " + segment + " for processor " + processorName);
        } else if (NULL_TOKEN == trackingToken) {
            return null;
        }
        return trackingToken;
    }

    @Override
    public void releaseClaim(@Nonnull String processorName, int segment) {
        // no-op, the in-memory implementation isn't accessible by multiple processes
    }

    @Override
    public void deleteToken(@Nonnull String processorName, int segment) throws UnableToClaimTokenException {
        tokens.remove(new ProcessAndSegment(processorName, segment));
    }

    @Override
    public void initializeSegment(TrackingToken token, @Nonnull String processorName, int segment)
            throws UnableToInitializeTokenException {
        TrackingToken previous = tokens.putIfAbsent(new ProcessAndSegment(processorName, segment),
                                                    token == null ? NULL_TOKEN : token);
        if (previous != null) {
            throw new UnableToInitializeTokenException("Token was already present");
        }
    }

    @Override
    public boolean requiresExplicitSegmentInitialization() {
        return true;
    }

    @Override
    public int[] fetchSegments(@Nonnull String processorName) {
        return tokens.keySet().stream()
                     .filter(ps -> ps.processorName.equals(processorName))
                     .map(ProcessAndSegment::getSegment)
                     .distinct()
                     .mapToInt(Number::intValue)
                     .sorted().toArray();
    }

    @Override
    public Optional<String> retrieveStorageIdentifier() {
        return Optional.of(identifier);
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
