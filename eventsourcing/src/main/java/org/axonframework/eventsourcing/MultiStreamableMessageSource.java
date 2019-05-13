package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implementation which allows for tracking processors to process messages from an arbitrary number of sources. The
 * order in which messages from each stream are consumed is configurable but defaults to the oldest message available
 * (using the event's timestamp). When the stream is polled for a specified duration, this duration is divided equally
 * between all sources.
 *
 * @author Greg Woods
 * @since 4.x
 */
public class MultiStreamableMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MultiStreamableMessageSource.class);

    private final Map<String,StreamableMessageSource<TrackedEventMessage<?>>> eventStreams;
    private MultiSourceTrackingToken trackingToken;
    private final Comparator<TrackedEventMessage<?>> comparator;
    private TrackedEventMessage<?> peekedMessage;

    /**
     * Instantiate a {@link MultiStreamableMessageSource} based on the fields contained in the
     * {@link MultiStreamableMessageSource.Builder}.
     * <p>
     *
     * @param builder the {@link MultiStreamableMessageSource.Builder} used to instantiate a
     * {@link MultiStreamableMessageSource} instance.
     */
    public MultiStreamableMessageSource(Builder builder){
        this.eventStreams = builder.messageSourceMap;
        this.comparator = builder.comparator;
    }

    /**
     * Instantiate a Builder to be able to create an {@link MultiStreamableMessageSource}. The configurable filed
     * {@code comparator}, which decides which message to process first if there is a choice defaults to the oldest
     * message available (using the event's timestamp).
     *
     * @return a Builder to be able to create a {@link MultiStreamableMessageSource}.
     */
    public static Builder builder(){
        return new Builder();
    }

    /**
     * Opens a stream for each event source at the specified token position.
     * @param trackingToken object containing the position in the stream or {@code null} to open a stream containing all
     *                      messages.
     * @return an instance of {@link MultiSourceMessageStream} with open streams for each event source.
     */
    @Override
    public MultiSourceMessageStream openStream(TrackingToken trackingToken) {
        if (trackingToken instanceof MultiSourceTrackingToken){
            this.trackingToken = (MultiSourceTrackingToken) trackingToken;
            return new MultiSourceMessageStream(eventStreams, (MultiSourceTrackingToken) trackingToken);
        }
        throw new IllegalArgumentException("Incompatible token type provided.");
    }

    @Override
    public MultiSourceTrackingToken createTailToken() {
        Map<String,TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((k,v) -> tokenMap.put(k,v.createTailToken()));

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createHeadToken() {
        Map<String,TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((k,v) -> tokenMap.put(k,v.createHeadToken()));

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenAt(Instant dateTime) {
        Map<String,TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((k,v) -> tokenMap.put(k,v.createTokenAt(dateTime)));

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenSince(Duration duration) {
        Map<String,TrackingToken> tokenMap = new HashMap<>();
        eventStreams.forEach((k,v) -> tokenMap.put(k,v.createTokenSince(duration)));

        return new MultiSourceTrackingToken(tokenMap);
    }

    private class MultiSourceMessageStream implements BlockingStream<TrackedEventMessage<?>>{

        private final Map<String, BlockingStream<TrackedEventMessage<?>>> messageSources;

        public MultiSourceMessageStream(Map<String, StreamableMessageSource<TrackedEventMessage<?>>> messageSources,
                                        MultiSourceTrackingToken trackingToken){
            this.messageSources = new HashMap<>();
            messageSources.forEach((k,v) -> this.messageSources.put(k,v.openStream(trackingToken.getTokenForStream(k))));
        }

        /**
         * @return true if one of the streams has a message available to be processed. Otherwise false.
         */
        @Override
        public boolean hasNextAvailable() {
            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                if (singleMessageSource.getValue().hasNextAvailable()){
                    return true;
                }
            }

            return false;
        }

        /**
         * Peeks each stream to check if a message is available. If more than one stream has a message it returns the
         * message chosen using the comparator.
         * @return message chosen using the comparator.
         */
        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            if (peekedMessage == null) {
                peekedMessage = doConsumeNext();
            }

            return Optional.ofNullable(peekedMessage);
        }

        private TrackedEventMessage<?> doConsumeNext() {
            HashMap<String, TrackedEventMessage> candidateMessagesToReturn = new HashMap<>();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                Optional<TrackedEventMessage<?>> peekedMessage = singleMessageSource.getValue().peek();
                peekedMessage.ifPresent(
                        trackedEventMessage ->
                                candidateMessagesToReturn.put(singleMessageSource.getKey(),
                                                              new GenericTrackedSourcedEventMessage(singleMessageSource.getKey(), trackedEventMessage)));
            }

            //select message to return from candidates
            final Optional<Map.Entry<String, TrackedEventMessage>> chosenMessage =
                    candidateMessagesToReturn.entrySet().stream().min((m1,m2) -> comparator.compare(m1.getValue(),m2.getValue()));

            // Ensure the chosen message is actually consumed from the stream
            return chosenMessage.map(e -> {
                String streamId = e.getKey();
                TrackedEventMessage<?> message = e.getValue();
                try {
                    return new GenericTrackedEventMessage<>(trackingToken.advancedTo(streamId, message.trackingToken()), messageSources.get(streamId).nextAvailable());
                } catch (InterruptedException ex) {
                    logger.warn("Thread Interrupted whilst consuming next message", ex);
                }
                return null;
            }).orElse(null);
        }

        /**
         * @param timeout the maximum number of time units to wait for messages to become available (divided equally
         *                between streams).
         * @param unit    the time unit for the timeout.
         * @return true if any stream has an available message. Otherwise false.
         * @throws InterruptedException when the thread is interrupted before the indicated time is up.
         */
        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            //divide the timeout equally between streams
            int pollingIntervalPerStream = timeout/messageSources.size();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                if (singleMessageSource.getValue().hasNextAvailable(pollingIntervalPerStream, unit)){
                    return true;
                }
            }

            return false;
        }

        /**
         * Checks each stream to see if a message is available. If more than one stream has a message it decides which
         * message to return using the {@link comparator}.
         * @return selected message using {@link comparator}.
         * @throws InterruptedException thrown if polling is interrupted during polling a stream.
         */
        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            //Return peekedMessage is available.
            if (peekedMessage != null) {
                TrackedEventMessage next = peekedMessage;
                peekedMessage = null;
                return next;
            }

            HashMap<String,TrackedSourcedEventMessage> candidateMessagesToReturn = new HashMap<>();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                Optional<TrackedEventMessage<?>> peekedMessage = singleMessageSource.getValue().peek();
                peekedMessage.ifPresent(trackedEventMessage ->
                                                candidateMessagesToReturn.put(singleMessageSource.getKey(),new GenericTrackedSourcedEventMessage(singleMessageSource.getKey(), trackedEventMessage)));
 ;
                }

            //get streamId of message to return after running the comparator
            String streamIdOfMessage =
                    candidateMessagesToReturn.entrySet().stream().min((m1,m2) -> comparator.compare(m1.getValue(),m2.getValue()))
                                                                .map(Map.Entry::getKey).orElse(null);

            // Actually consume the message from the stream
            TrackedEventMessage<?> messageToReturn = messageSources.get(streamIdOfMessage).nextAvailable();

            //update the composite token
            final MultiSourceTrackingToken newTrackingToken = trackingToken.advancedTo(streamIdOfMessage,messageToReturn.trackingToken());
            trackingToken = newTrackingToken;

            logger.debug("Message consumed from stream: {}", streamIdOfMessage);
            return new GenericTrackedEventMessage<>(newTrackingToken, messageToReturn);
        }

        /**
         * Calls close on each of the streams.
         */
        @Override
        public void close() {
            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                singleMessageSource.getValue().close();
            }
        }
    }


    /**
     * Builder class to instantiate a {@link MultiStreamableMessageSource}. The configurable filed
     * {@code comparator}, which decides which message to process first if there is a choice defaults to the oldest
     * message available (using the event's timestamp).
     */
    public static class Builder{

        private Comparator<TrackedEventMessage<?>> comparator = Comparator.comparing(EventMessage::getTimestamp);
        private Map<String,StreamableMessageSource<TrackedEventMessage<?>>> messageSourceMap = new HashMap<>();

        /**
         * Adds a message source to the list of sources.
         * @param messageSourceName a unique name identifying the stream.
         * @param messageSource the message source to be added.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder addMessageSource(String messageSourceName, StreamableMessageSource<TrackedEventMessage<?>> messageSource){
            Assert.isFalse(messageSourceMap.containsKey(messageSourceName),() -> "the messageSource name must be unique");
            Assert.isFalse(messageSourceMap.containsValue(messageSource), () -> "this message source is duplicated");

            messageSourceMap.put(messageSourceName, messageSource);
            return this;
        }

        /**
         * Overrides the default comparator (which returns the oldest event available).
         * @param comparator the comparator to use when deciding on which message to return.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder withComparator(Comparator<TrackedEventMessage<?>> comparator){
            this.comparator = comparator;
            return this;
        }

        /**
         * Initializes a {@link MultiStreamableMessageSource} as specified through this Builder.
         *
         * @return a {@link MultiStreamableMessageSource} as specified through this Builder.
         */
        public MultiStreamableMessageSource build(){
            return new MultiStreamableMessageSource(this);
        }
    }
}
