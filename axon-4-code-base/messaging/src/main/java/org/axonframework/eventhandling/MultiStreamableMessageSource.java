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

package org.axonframework.eventhandling;

import org.axonframework.common.stream.BlockingStream;
import org.axonframework.messaging.StreamableMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Implementation which allows for tracking processors to process messages from an arbitrary number of sources. The
 * order in which messages from each stream are consumed is configurable but defaults to the oldest message available
 * (using the event's timestamp). When the stream is polled for a specified duration, each stream is called with
 * {@link MultiSourceBlockingStream#hasNextAvailable()} except for the last stream configured by the
 * {@link Builder#addMessageSource(String, StreamableMessageSource)} or by explicit configuration using
 * {@link Builder#longPollingSource(String)}. This stream long polls for a fraction of the specified duration before
 * looping through the sources again repeating until the duration has been met. This ensures the highest chance of a
 * consumable message being found.
 *
 * @author Greg Woods
 * @since 4.2
 */
public class MultiStreamableMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MultiStreamableMessageSource.class);

    private final List<IdentifiedStreamableMessageSource> eventStreams;
    private final Comparator<Map.Entry<String, TrackedEventMessage<?>>> trackedEventComparator;

    /**
     * Instantiate a Builder to be able to create an {@link MultiStreamableMessageSource}. The configurable field
     * {@code trackedEventComparator}, which decides which message to process first if there is a choice defaults to the
     * oldest message available (using the event's timestamp).
     *
     * @return A Builder to be able to create a {@link MultiStreamableMessageSource}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link MultiStreamableMessageSource} based on the fields contained in the {@link Builder}.
     *
     * @param builder The {@link Builder} used to instantiate a {@link MultiStreamableMessageSource} instance.
     */
    protected MultiStreamableMessageSource(Builder builder) {
        this.eventStreams = builder.messageSources();
        this.trackedEventComparator = builder.trackedEventComparator;
    }

    /**
     * Opens a stream for each event source at the specified token position.
     *
     * @param trackingToken Object containing the position in the stream or {@code null} to open a stream containing all
     *                      messages.
     * @return An instance of {@link MultiSourceBlockingStream} with open streams for each event source.
     */
    @Override
    public MultiSourceBlockingStream openStream(TrackingToken trackingToken) {
        if (trackingToken == null) {
            return openStream(createTailToken());
        } else if (trackingToken instanceof MultiSourceTrackingToken) {
            return new MultiSourceBlockingStream(
                    eventStreams, (MultiSourceTrackingToken) trackingToken, trackedEventComparator
            );
        }
        throw new IllegalArgumentException("Incompatible token type provided.");
    }

    @Override
    public MultiSourceTrackingToken createTailToken() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach(streamableMessageSource -> tokenMap.put(streamableMessageSource.sourceId(),
                                                                     streamableMessageSource.createTailToken()));
        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createHeadToken() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach(streamableMessageSource -> tokenMap.put(streamableMessageSource.sourceId(),
                                                                     streamableMessageSource.createHeadToken()));
        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenAt(Instant dateTime) {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach(streamableMessageSource -> tokenMap.put(streamableMessageSource.sourceId(),
                                                                     streamableMessageSource.createTokenAt(dateTime)));
        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenSince(Duration duration) {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach(streamableMessageSource -> tokenMap.put(
                streamableMessageSource.sourceId(), streamableMessageSource.createTokenSince(duration))
        );
        return new MultiSourceTrackingToken(tokenMap);
    }

    /**
     * Builder class to instantiate a {@link MultiStreamableMessageSource}. The configurable filed
     * {@code trackedEventComparator}, which decides which message to process first if there is a choice defaults to the
     * oldest message available (using the event's timestamp). The stream on which long polling is done for
     * {@link MultiSourceBlockingStream#hasNextAvailable(int, TimeUnit)} is also configurable.
     */
    public static class Builder {

        private Comparator<Map.Entry<String, TrackedEventMessage<?>>> trackedEventComparator =
                Comparator.comparing((Map.Entry<String, TrackedEventMessage<?>> t) -> t.getValue().getTimestamp());
        private final Map<String, StreamableMessageSource<TrackedEventMessage<?>>> messageSourceMap = new LinkedHashMap<>();
        private String longPollingSource = "";

        /**
         * Adds a message source to the list of sources.
         *
         * @param messageSourceId A unique name identifying the stream.
         * @param messageSource   The message source to be added.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder addMessageSource(String messageSourceId,
                                        StreamableMessageSource<TrackedEventMessage<?>> messageSource) {
            assertThat(messageSourceId, sourceName -> !messageSourceMap.containsKey(sourceName),
                       "the messageSource name must be unique");
            messageSourceMap.put(messageSourceId, messageSource);
            return this;
        }

        /**
         * Overrides the default trackedEventComparator. The default trackedEventComparator returns the oldest event
         * available: {@code Comparator.comparing(EventMessage::getTimestamp)}.
         *
         * @param trackedEventComparator The trackedEventComparator to use when deciding on which message to return.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder trackedEventComparator(
                Comparator<Map.Entry<String, TrackedEventMessage<?>>> trackedEventComparator) {
            this.trackedEventComparator = trackedEventComparator;
            return this;
        }

        /**
         * Select the message source which is most suitable for long polling. To prevent excessive polling on all
         * sources. If a source is not configured explicitly then it defaults to the last source provided. it is
         * preferable to do the majority of polling on a single source. All other streams will be checked first using
         * {@link BlockingStream#hasNextAvailable()} before {@link BlockingStream#hasNextAvailable(int, TimeUnit)} is
         * called on the source chosen for long polling. This is then repeated multiple times to increase the chance of
         * successfully finding a message before the timeout. If no particular source is configured, long polling will
         * default to the last configured source whilst other streams will be polled using
         * {@link BlockingStream#hasNextAvailable()}.
         *
         * @param longPollingSource The {@code messageSourceName} on which to do the long polling.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder longPollingSource(String longPollingSource) {
            assertThat(longPollingSource, messageSourceMap::containsKey,
                       "Current configuration does not contain this message source");
            this.longPollingSource = longPollingSource;
            return this;
        }

        /**
         * Initializes a {@link MultiStreamableMessageSource} as specified through this Builder.
         *
         * @return a {@link MultiStreamableMessageSource} as specified through this Builder.
         */
        public MultiStreamableMessageSource build() {
            return new MultiStreamableMessageSource(this);
        }

        private List<IdentifiedStreamableMessageSource> messageSources() {
            List<IdentifiedStreamableMessageSource> sourceList = new ArrayList<>();
            messageSourceMap.forEach((sourceId, source) -> {
                if (!longPollingSource.equals(sourceId)) {
                    sourceList.add(new IdentifiedStreamableMessageSource(sourceId, source));
                }
            });
            // Add the long polling source last, if it was defined.
            if (messageSourceMap.containsKey(longPollingSource)) {
                sourceList.add(new IdentifiedStreamableMessageSource(
                        longPollingSource, messageSourceMap.get(longPollingSource)
                ));
            }
            return sourceList;
        }
    }

    /**
     * Wrapper around a {@link StreamableMessageSource} that hold the name of the source.
     */
    private static class IdentifiedStreamableMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

        private final StreamableMessageSource<TrackedEventMessage<?>> delegate;
        private final String sourceId;

        public IdentifiedStreamableMessageSource(String sourceId,
                                                 StreamableMessageSource<TrackedEventMessage<?>> delegate) {
            this.delegate = delegate;
            this.sourceId = sourceId;
        }

        @Override
        public BlockingStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
            return delegate.openStream(trackingToken);
        }

        @Override
        public TrackingToken createTailToken() {
            return delegate.createTailToken();
        }

        @Override
        public TrackingToken createHeadToken() {
            return delegate.createHeadToken();
        }

        @Override
        public TrackingToken createTokenAt(Instant dateTime) {
            return delegate.createTokenAt(dateTime);
        }

        @Override
        public TrackingToken createTokenSince(Duration duration) {
            return delegate.createTokenSince(duration);
        }

        public String sourceId() {
            return sourceId;
        }
    }

    /**
     * Wrapper around a {@link BlockingStream} that keeps track of the name of the source in its container.
     */
    private static class SourceIdAwareBlockingStream implements BlockingStream<TrackedEventMessage<?>> {

        private final String sourceId;
        private final BlockingStream<TrackedEventMessage<?>> delegate;

        public SourceIdAwareBlockingStream(String sourceId, BlockingStream<TrackedEventMessage<?>> delegate) {
            this.sourceId = sourceId;
            this.delegate = delegate;
        }

        @Override
        public boolean hasNextAvailable() {
            return delegate.hasNextAvailable();
        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            return delegate.peek();
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            return delegate.hasNextAvailable(timeout, unit);
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            return delegate.nextAvailable();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public Stream<TrackedEventMessage<?>> asStream() {
            return delegate.asStream();
        }

        @Override
        public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredMessage) {
            delegate.skipMessagesWithPayloadTypeOf(ignoredMessage);
        }

        @Override
        public boolean setOnAvailableCallback(Runnable callback) {
            return delegate.setOnAvailableCallback(callback);
        }
    }

    /**
     * A {@link BlockingStream} implementation that delegates requests to one or more underlying streams.
     */
    private static class MultiSourceBlockingStream implements BlockingStream<TrackedEventMessage<?>> {

        private final List<SourceIdAwareBlockingStream> messageStreams;
        private final Map<String, SourceIdAwareBlockingStream> streamBySourceId;
        private final Comparator<? super Map.Entry<String, TrackedEventMessage<?>>> trackedEventComparator;
        private MultiSourceTrackingToken trackingToken;
        private TrackedEventMessage<?> peekedMessage;

        /**
         * Construct a new {@link MultiSourceBlockingStream} from the message sources provided using the
         * {@link MultiSourceTrackingToken} and open each underlying stream with its respective token position.
         *
         * @param messageSources         The sources backing this {@link MultiSourceBlockingStream}.
         * @param trackingToken          The {@link MultiSourceTrackingToken} containing the positions to open the
         *                               stream.
         * @param trackedEventComparator The comparator used to choose the message to read when multiple sources have
         *                               messages available.
         */
        public MultiSourceBlockingStream(Iterable<IdentifiedStreamableMessageSource> messageSources,
                                         MultiSourceTrackingToken trackingToken,
                                         Comparator<? super Map.Entry<String, TrackedEventMessage<?>>> trackedEventComparator) {
            this.trackedEventComparator = trackedEventComparator;
            this.messageStreams = new ArrayList<>();
            this.trackingToken = trackingToken;
            this.streamBySourceId = new HashMap<>();
            try {
                messageSources.forEach(src -> {
                    SourceIdAwareBlockingStream stream = new SourceIdAwareBlockingStream(
                            src.sourceId(),
                            src.openStream(trackingToken.getTokenForStream(src.sourceId()))
                    );
                    this.messageStreams.add(stream);
                    this.streamBySourceId.put(src.sourceId(), stream);
                });
            } catch (Exception e) {
                messageStreams.forEach(SourceIdAwareBlockingStream::close);
                throw e;
            }
        }

        /**
         * Checks if a message is available to consume on any of the streams.
         *
         * @return {@code true} if one of the streams has a message available to be processed and {@code false}
         * otherwise.
         */
        @Override
        public boolean hasNextAvailable() {
            return peekedMessage != null || messageStreams.stream().anyMatch(BlockingStream::hasNextAvailable);
        }

        /**
         * Peeks each stream to check if a message is available. If more than one stream has a message it returns the
         * message chosen using the trackedEventComparator.
         *
         * @return message chosen using the trackedEventComparator.
         */
        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            if (peekedMessage == null) {
                peekedMessage = doConsumeNext();
            }

            return Optional.ofNullable(peekedMessage);
        }

        private TrackedEventMessage<?> doConsumeNext() {
            HashMap<String, TrackedEventMessage<?>> candidateMessagesToReturn = new HashMap<>();

            peekForMessages(candidateMessagesToReturn);

            //select message to return from candidates
            final Optional<Map.Entry<String, TrackedEventMessage<?>>> chosenMessage =
                    candidateMessagesToReturn.entrySet().stream().min(trackedEventComparator);

            // Ensure the chosen message is actually consumed from the stream
            return chosenMessage.map(e -> {
                String streamId = e.getKey();
                TrackedEventMessage<?> message = e.getValue();
                try {
                    MultiSourceTrackingToken advancedToken =
                            this.trackingToken.advancedTo(streamId, message.trackingToken());
                    return messageSource(streamId).nextAvailable().withTrackingToken(advancedToken);
                } catch (InterruptedException ex) {
                    logger.warn("Thread Interrupted whilst consuming next message", ex);
                    Thread.currentThread().interrupt();
                }
                return null;
            }).orElse(null);
        }

        private BlockingStream<TrackedEventMessage<?>> messageSource(String sourceId) {
            return streamBySourceId.get(sourceId);
        }

        /**
         * Checks whether the next message on one of the streams is available. If a message is available when this
         * method is invoked this method returns immediately. If not, this method will block until a message becomes
         * available, returning {@code true} or until the given {@code timeout} expires, returning {@code false}.
         * <p>
         * The timeout is spent polling on one particular stream (specified by {@link Builder#longPollingSource(String)}
         * or by default to last stream provided to {@link Builder#addMessageSource(String, StreamableMessageSource)}.
         * An initial sweep of the sources is done before the last source is polled for a fraction of the specified
         * duration before looping through the sources again repeating until the timeout has been met. This ensure the
         * highest chance of a consumable message being found.
         * <p>
         * To check if the stream has messages available now, pass a zero {@code timeout}.
         *
         * @param timeout the maximum number of time units to wait for messages to become available. This time is spent
         *                polling on one particular stream (specified by {@link Builder#longPollingSource(String)} or by
         *                default to last stream provided to
         *                {@link Builder#addMessageSource(String, StreamableMessageSource)}. An initial sweep of the
         *                sources is done before the last source is polled for a fraction of the specified duration
         *                before looping through the sources again repeating until the timeout has
         * @param unit    the time unit for the timeout.
         * @return {@code true} if any stream has an available message and {@code false} otherwise.
         * @throws InterruptedException when the thread is interrupted before the indicated time is up.
         */
        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            if (peekedMessage != null) {
                return true;
            }
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            long longPollTime = unit.toMillis(timeout) / 10;

            while (System.currentTimeMillis() < deadline) {
                Iterator<SourceIdAwareBlockingStream> it = messageStreams.iterator();

                while (it.hasNext()) {
                    SourceIdAwareBlockingStream current = it.next();


                    if (it.hasNext()) {
                        //check if messages are available on other (non long polling) streams
                        if (current.hasNextAvailable()) {
                            return true;
                        }
                    } else {
                        //for the last stream (the long polling stream) poll the message source for a fraction of the timeout
                        if (current.hasNextAvailable((int) Math
                                .min(longPollTime, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }

        /**
         * Checks each stream to see if a message is available. If more than one stream has a message it decides which
         * message to return using the {@link #trackedEventComparator}.
         *
         * @return selected message using {@link #trackedEventComparator}.
         * @throws InterruptedException thrown if polling is interrupted during polling a stream.
         */
        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            //Return peekedMessage if available.
            if (peekedMessage != null) {
                TrackedEventMessage<?> next = peekedMessage;
                peekedMessage = null;
                trackingToken = (MultiSourceTrackingToken) next.trackingToken();
                return next;
            }

            HashMap<String, TrackedEventMessage<?>> candidateMessagesToReturn = new HashMap<>();

            //block until there is a message to consume
            while (candidateMessagesToReturn.size() == 0) {
                peekForMessages(candidateMessagesToReturn);
            }

            //get streamId of message to return after running the trackedEventComparator
            String streamIdOfMessage =
                    candidateMessagesToReturn.entrySet().stream()
                                             .min(trackedEventComparator)
                                             .map(Map.Entry::getKey).orElse(null);

            // Actually consume the message from the stream
            TrackedEventMessage<?> messageToReturn = messageSource(streamIdOfMessage).nextAvailable();

            //update the composite token
            final MultiSourceTrackingToken newTrackingToken = trackingToken.advancedTo(streamIdOfMessage,
                                                                                       messageToReturn.trackingToken());
            trackingToken = newTrackingToken;

            logger.debug("Message consumed from stream: {}", streamIdOfMessage);
            return messageToReturn.withTrackingToken(newTrackingToken);
        }

        private void peekForMessages(Map<String, TrackedEventMessage<?>> candidateMessagesToReturn) {
            for (SourceIdAwareBlockingStream singleMessageSource : messageStreams) {
                Optional<TrackedEventMessage<?>> currentPeekedMessage = singleMessageSource.peek();
                currentPeekedMessage.ifPresent(
                        trackedEventMessage -> candidateMessagesToReturn
                                .put(singleMessageSource.sourceId, trackedEventMessage));
            }
        }

        /**
         * Calls close on each of the streams.
         */
        @Override
        public void close() {
            messageStreams.forEach(SourceIdAwareBlockingStream::close);
        }

        @Override
        public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredMessage) {
            messageStreams.forEach(stream -> stream.skipMessagesWithPayloadTypeOf(ignoredMessage));
        }

        /**
         * Set a {@code callback} to be invoked once new messages are available on any of the streams this
         * {@link BlockingStream} implementations contains. Returns {@code true} if this functionality is supported by
         * all contained streams and {@code false} otherwise.
         *
         * @param callback a {@link Runnable}
         * @return {@code true} if all contained {@link BlockingStream} implementations return true for
         * {@link BlockingStream#setOnAvailableCallback(Runnable)}, {@code false otherwise}
         */
        @Override
        public boolean setOnAvailableCallback(Runnable callback) {
            return messageStreams.stream()
                                 .map(stream -> stream.setOnAvailableCallback(callback))
                                 .reduce((resultOne, resultTwo) -> resultOne && resultTwo)
                                 .orElse(false);
        }
    }
}
