package org.axonframework.eventsourcing;

import org.axonframework.common.BuilderUtils;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GenericTrackedSourcedEventMessage;
import org.axonframework.eventhandling.MultiSourceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackedSourcedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.StreamableMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementation which allows for tracking processors to process messages from an arbitrary number of sources. The
 * order in which messages from each stream are consumed is configurable but defaults to the oldest message available
 * (using the event's timestamp). When the stream is polled for a specified duration, each stream is called with
 * {@link MultiSourceMessageStream#hasNextAvailable()} except for the last stream configured by the
 * {@link MultiStreamableMessageSource.Builder#addMessageSource(String, StreamableMessageSource)} or by explicit
 * configuration using {@link MultiStreamableMessageSource.Builder#longPollingSource(String)}. This stream long
 * polls for a fraction of the specified duration before looping through the sources again repeating until the duration
 * has been met. This ensure the highest chance of a consumable message being found.
 *
 * @author Greg Woods
 * @since 4.2
 */
public class MultiStreamableMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MultiStreamableMessageSource.class);

    private final Map<String, StreamableMessageSource<TrackedEventMessage<?>>> eventStreams;
    private MultiSourceTrackingToken trackingToken;
    private final Comparator<TrackedSourcedEventMessage<?>> trackedEventComparator;
    private TrackedEventMessage<?> peekedMessage;

    /**
     * Instantiate a {@link MultiStreamableMessageSource} based on the fields contained in the
     * {@link MultiStreamableMessageSource.Builder}.
     * <p>
     *
     * @param builder the {@link MultiStreamableMessageSource.Builder} used to instantiate a
     *                {@link MultiStreamableMessageSource} instance.
     */
    public MultiStreamableMessageSource(Builder builder) {
        this.eventStreams = builder.messageSourceMap;

        //ensure longPollingSource is last item in the LinkedHashMap
        if (!"".equals(builder.longPollingSource)) {
            StreamableMessageSource<TrackedEventMessage<?>> sourceToReAdd = this.eventStreams
                    .remove(builder.longPollingSource);
            this.eventStreams.put(builder.longPollingSource, sourceToReAdd);
        }

        this.trackedEventComparator = builder.trackedEventComparator;
    }

    /**
     * Instantiate a Builder to be able to create an {@link MultiStreamableMessageSource}. The configurable field
     * {@code trackedEventComparator}, which decides which message to process first if there is a choice defaults to the oldest
     * message available (using the event's timestamp).
     *
     * @return a Builder to be able to create a {@link MultiStreamableMessageSource}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Opens a stream for each event source at the specified token position.
     *
     * @param trackingToken object containing the position in the stream or {@code null} to open a stream containing all
     *                      messages.
     * @return an instance of {@link MultiSourceMessageStream} with open streams for each event source.
     */
    @Override
    public MultiSourceMessageStream openStream(TrackingToken trackingToken) {
        if (trackingToken instanceof MultiSourceTrackingToken) {
            this.trackingToken = (MultiSourceTrackingToken) trackingToken;
            return new MultiSourceMessageStream(eventStreams, (MultiSourceTrackingToken) trackingToken);
        }
        throw new IllegalArgumentException("Incompatible token type provided.");
    }

    @Override
    public MultiSourceTrackingToken createTailToken() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((streamableSourceName, streamableMessageSource) ->
                                     tokenMap.put(streamableSourceName, streamableMessageSource.createTailToken()));

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createHeadToken() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((streamableSourceName, streamableMessageSource) ->
                                     tokenMap.put(streamableSourceName, streamableMessageSource.createHeadToken()));

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenAt(Instant dateTime) {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((streamableSourceName, streamableMessageSource) ->
                                     tokenMap.put(streamableSourceName, streamableMessageSource.createTokenAt(dateTime)));

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenSince(Duration duration) {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((streamableSourceName, streamableMessageSource) ->
                                     tokenMap.put(streamableSourceName, streamableMessageSource.createTokenSince(duration)));

        return new MultiSourceTrackingToken(tokenMap);
    }

    private class MultiSourceMessageStream implements BlockingStream<TrackedEventMessage<?>> {

        private final Map<String, BlockingStream<TrackedEventMessage<?>>> messageSources;

        /**
         * Construct a new {@link MultiSourceMessageStream} from the message sources provided using the
         * {@link MultiSourceTrackingToken} and open each underlying stream with its respective token position.
         *
         * @param messageSources that sources that make up this {@link MultiSourceMessageStream}.
         * @param trackingToken  the {@link MultiSourceTrackingToken} containing the positions to open the stream.
         */
        public MultiSourceMessageStream(Map<String, StreamableMessageSource<TrackedEventMessage<?>>> messageSources,
                                        MultiSourceTrackingToken trackingToken) {
            this.messageSources = new HashMap<>();
            messageSources.forEach((streamableSourceName, streamableMessageSource) -> this.messageSources
                    .put(streamableSourceName, streamableMessageSource.openStream(trackingToken.getTokenForStream(streamableSourceName))));
        }

        /**
         * Checks if a message is available to consume on any of the streams.
         * @return true if one of the streams has a message available to be processed. Otherwise false.
         */
        @Override
        public boolean hasNextAvailable() {
            return messageSources.entrySet().stream().anyMatch(entry -> entry.getValue().hasNextAvailable());

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
            HashMap<String, TrackedSourcedEventMessage> candidateMessagesToReturn = new HashMap<>();

            peekForMessages(candidateMessagesToReturn);

            //select message to return from candidates
            final Optional<Map.Entry<String, TrackedSourcedEventMessage>> chosenMessage =
                    candidateMessagesToReturn.entrySet().stream().min((m1, m2) -> trackedEventComparator
                            .compare(m1.getValue(), m2.getValue()));

            // Ensure the chosen message is actually consumed from the stream
            return chosenMessage.map(e -> {
                String streamId = e.getKey();
                TrackedEventMessage<?> message = e.getValue();
                try {
                    return new GenericTrackedEventMessage<>(trackingToken.advancedTo(streamId, message.trackingToken()),
                                                            messageSources.get(streamId).nextAvailable());
                } catch (InterruptedException ex) {
                    logger.warn("Thread Interrupted whilst consuming next message", ex);
                }
                return null;
            }).orElse(null);
        }

        /**
         * Checks whether or not the next message on one of the streams is available. If a message is available when this
         * method is invoked this method returns immediately. If not, this method will block until a message becomes
         * available, returning {@code true} or until the given {@code timeout} expires, returning
         * {@code false}.
         * <p>
         * The timeout is spent polling on one particular stream (specified by {@link Builder#longPollingSource(String)}
         * or by default to last stream provided to {@link Builder#addMessageSource(String,StreamableMessageSource)}.
         * An initial sweep of the sources is done before the last source is polled for a fraction of the specified
         * duration before looping through the sources again repeating until the timeout has been met. This ensure the
         * highest chance of a consumable message being found.
         * <p>
         * To check if the stream has messages available now, pass a zero {@code timeout}.
         *
         * @param timeout the maximum number of time units to wait for messages to become available. This time
         *                is spent polling on one particular stream (specified by {@link Builder#longPollingSource(String)}
         *                or by default to last stream provided to {@link Builder#addMessageSource(String,
         *                been met. This ensure the highest chance of a consumable message being found.
         *                StreamableMessageSource)}.
         *                An initial sweep of the sources is done before the last source is polled for a fraction of the
         *                specified duration before looping through the sources again repeating until the timeout has
         * @param unit    the time unit for the timeout.
         * @return true if any stream has an available message. Otherwise false.
         *
         * @throws InterruptedException when the thread is interrupted before the indicated time is up.
         */
        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            int longPollTime = timeout / 10;

            while (System.currentTimeMillis() < deadline) {
                Iterator<Map.Entry<String, BlockingStream<TrackedEventMessage<?>>>> it = messageSources.entrySet()
                                                                                                       .iterator();

                while (it.hasNext()) {
                    Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> current = it.next();


                    if (it.hasNext()) {
                        //check if messages are available on other (non long polling) streams
                        if (current.getValue().hasNextAvailable()) {
                            return true;
                        }
                    } else {
                        //for the last stream (the long polling stream) poll the message source for a fraction of the timeout
                        if (current.getValue().hasNextAvailable((int) Math
                                .min(longPollTime, deadline - System.currentTimeMillis()), unit)) {
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
         *
         * @throws InterruptedException thrown if polling is interrupted during polling a stream.
         */
        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            //Return peekedMessage if available.
            if (peekedMessage != null) {
                TrackedEventMessage next = peekedMessage;
                peekedMessage = null;
                return next;
            }

            HashMap<String, TrackedSourcedEventMessage> candidateMessagesToReturn = new HashMap<>();

            //block until there is a message to consume
            while (candidateMessagesToReturn.size() == 0) {
                peekForMessages(candidateMessagesToReturn);
            }

            //get streamId of message to return after running the trackedEventComparator
            String streamIdOfMessage =
                    candidateMessagesToReturn.entrySet().stream()
                                             .min((m1, m2) -> trackedEventComparator.compare(m1.getValue(), m2.getValue()))
                                             .map(Map.Entry::getKey).orElse(null);

            // Actually consume the message from the stream
            TrackedEventMessage<?> messageToReturn = messageSources.get(streamIdOfMessage).nextAvailable();

            //update the composite token
            final MultiSourceTrackingToken newTrackingToken = trackingToken.advancedTo(streamIdOfMessage,
                                                                                       messageToReturn.trackingToken());
            trackingToken = newTrackingToken;

            logger.debug("Message consumed from stream: {}", streamIdOfMessage);
            return new GenericTrackedEventMessage<>(newTrackingToken, messageToReturn);
        }

        private void peekForMessages(Map<String, TrackedSourcedEventMessage> candidateMessagesToReturn) {
            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources
                    .entrySet()) {
                Optional<TrackedEventMessage<?>> currentPeekedMessage = singleMessageSource.getValue().peek();
                currentPeekedMessage.ifPresent(
                        trackedEventMessage -> candidateMessagesToReturn
                                .put(singleMessageSource.getKey(),
                                     new GenericTrackedSourcedEventMessage(singleMessageSource.getKey(), trackedEventMessage)));
            }
        }

        /**
         * Calls close on each of the streams.
         */
        @Override
        public void close() {
            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources
                    .entrySet()) {
                singleMessageSource.getValue().close();
            }
        }
    }


    /**
     * Builder class to instantiate a {@link MultiStreamableMessageSource}. The configurable filed
     * {@code trackedEventComparator}, which decides which message to process first if there is a choice defaults to the oldest
     * message available (using the event's timestamp). The stream on which long polling is done for
     * {@link MultiSourceMessageStream#hasNextAvailable(int, TimeUnit)} is also configurable.
     */
    public static class Builder {

        private Comparator<TrackedSourcedEventMessage<?>> trackedEventComparator = Comparator.comparing(EventMessage::getTimestamp);
        private Map<String, StreamableMessageSource<TrackedEventMessage<?>>> messageSourceMap = new LinkedHashMap<>();
        private String longPollingSource = "";

        /**
         * Adds a message source to the list of sources.
         *
         * @param messageSourceName a unique name identifying the stream.
         * @param messageSource     the message source to be added.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder addMessageSource(String messageSourceName,
                                        StreamableMessageSource<TrackedEventMessage<?>> messageSource) {
            BuilderUtils.assertThat(messageSourceName, sourceName -> !messageSourceMap.containsKey(sourceName),
                                    "the messageSource name must be unique");
            BuilderUtils.assertThat(messageSource, source -> !messageSourceMap.containsKey(source),
                                    "this message source is duplicated");

            messageSourceMap.put(messageSourceName, messageSource);
            return this;
        }

        /**
         * Overrides the default trackedEventComparator. The default trackedEventComparator returns the oldest event available:
         * {@code Comparator.comparing(EventMessage::getTimestamp);}
         *
         * @param trackedEventComparator the trackedEventComparator to use when deciding on which message to return.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder trackedEventComparator(Comparator<TrackedSourcedEventMessage<?>> trackedEventComparator) {
            this.trackedEventComparator = trackedEventComparator;
            return this;
        }


        /**
         * Select the message source which is most suitable for long polling. To prevent excessive polling on all
         * sources. If a source is not configured explicitly then it defaults to the last source provided.
         * it is preferable to do the majority of polling on a single source. All other streams will be checked first
         * using {@link BlockingStream#hasNextAvailable()} before {@link BlockingStream#hasNextAvailable(int, TimeUnit)}
         * is
         * called on the source chosen for long polling. This is then repeated multiple times to increase the chance of
         * successfully finding a message before the timeout. If no particular source is configured, long polling will default
         * to the last configured source whilst other streams will be polled using {@link BlockingStream#hasNextAvailable()}
         *
         * @param longPollingSource the {@code messageSourceName} on which to do the long polling.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder longPollingSource(String longPollingSource) {
            BuilderUtils.assertThat(longPollingSource, sourceName -> messageSourceMap.containsKey(sourceName),
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
    }
}
