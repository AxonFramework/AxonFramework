package org.axonframework.eventhandling;

import org.axonframework.common.stream.BlockingStream;
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

public class MultiStreamableMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MultiStreamableMessageSource.class);

    public static final Comparator<Map.Entry<String, TrackedEventMessage>> DEFAULT_COMPARATOR = Comparator
                                                                                                  .comparing(m -> m
                                                                                                          .getValue()
                                                                                                          .getTimestamp());
    private Map<String,StreamableMessageSource<TrackedEventMessage<?>>> eventStreams;
    private MultiSourceTrackingToken trackingToken;
    private Comparator<? super Map.Entry<String, TrackedEventMessage>> comparator;

    public MultiStreamableMessageSource(Map<String,StreamableMessageSource<TrackedEventMessage<?>>> eventStreams){
        this(eventStreams, DEFAULT_COMPARATOR);
    }
    public MultiStreamableMessageSource(Map<String,StreamableMessageSource<TrackedEventMessage<?>>> eventStreams, Comparator<? super Map.Entry<String, TrackedEventMessage>> comparator){
        this.eventStreams=eventStreams;
        this.comparator = comparator;
    }

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

        eventStreams.forEach((k,v) ->
                tokenMap.put(k,v.createTailToken())
        );

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createHeadToken() {
        Map<String,TrackingToken> tokenMap = new HashMap<>();

        eventStreams.forEach((k,v) ->
                                     tokenMap.put(k,v.createHeadToken())
        );

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenAt(Instant dateTime) {
        Map<String,TrackingToken> tokenMap = new HashMap<>();

        eventStreams.forEach((k,v) ->
                                     tokenMap.put(k,v.createTokenAt(dateTime))
        );

        return new MultiSourceTrackingToken(tokenMap);
    }

    @Override
    public MultiSourceTrackingToken createTokenSince(Duration duration) {
        Map<String,TrackingToken> tokenMap = new HashMap<>();

        eventStreams.forEach((k,v) ->
                                     tokenMap.put(k,v.createTokenSince(duration))
        );

        return new MultiSourceTrackingToken(tokenMap);
    }


    private class MultiSourceMessageStream implements BlockingStream<TrackedEventMessage<?>>{

        private final Map<String, BlockingStream<TrackedEventMessage<?>>> messageSources;

        public MultiSourceMessageStream(Map<String, StreamableMessageSource<TrackedEventMessage<?>>> messageSources,
                                        MultiSourceTrackingToken trackingToken){
            this.messageSources = new HashMap<>();
            messageSources.forEach((k,v) ->
                    this.messageSources.put(k,v.openStream(trackingToken.getTokenForStream(k)))
            );
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
         * message with the oldest timestamp.
         * @return message wih oldest timestamp.
         */
        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            HashMap<String,TrackedEventMessage> candidateMessagesToReturn = new HashMap<>();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                Optional<TrackedEventMessage<?>> peekedMessage = singleMessageSource.getValue().peek();
                peekedMessage.ifPresent(
                        trackedEventMessage -> candidateMessagesToReturn.put(singleMessageSource.getKey(), trackedEventMessage));
            }

            //find possible oldest message
            final Optional<Map.Entry<String, TrackedEventMessage>> min =
                    candidateMessagesToReturn.entrySet().stream().min(comparator);

            if (min.isPresent()){
                String streamId = min.get().getKey();
                TrackedEventMessage message = min.get().getValue();
                return Optional.of(new GenericTrackedEventMessage<>(trackingToken.advancedTo(streamId, message.trackingToken()), message));
            } else {
                return Optional.empty();
            }
        }

        /**
         *
         * @param timeout the maximum number of time units to wait for messages to become available (this is shared
         *                between streams)
         * @param unit    the time unit for the timeout
         * @return true if any stream has an available message. Otherwise false.
         * @throws InterruptedException
         */
        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            //Share the timeout between streams
            int pollingIntervalPerStream = timeout/messageSources.size();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                if (singleMessageSource.getValue().hasNextAvailable(pollingIntervalPerStream,unit)){
                    return true;
                }
            }

            return false;
        }

        /**
         * Checks each stream to check if a message is available. If more than one stream has a message it returns the
         * message with the earliest timestamp.
         * @return message with the oldest timestamp
         * @throws InterruptedException thrown if polling is interrupted during polling a stream.
         */
        @Override
        public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
            HashMap<String,TrackedEventMessage> candidateMessagesToReturn = new HashMap<>();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                Optional<TrackedEventMessage<?>> peekedMessage = singleMessageSource.getValue().peek();
                peekedMessage.ifPresent(trackedEventMessage ->
                                                candidateMessagesToReturn.put(singleMessageSource.getKey(),trackedEventMessage));
 ;
                }

            //get streamId of message to return after running the comparator
            String streamIdOfMessage =  candidateMessagesToReturn.entrySet().stream().min(comparator).map(Map.Entry::getKey).orElse(null);

            // Actually consume the message from the stream
            TrackedEventMessage<?> messageToReturn = messageSources.get(streamIdOfMessage).nextAvailable();

            //update the composite token
            final MultiSourceTrackingToken newTrackingToken = trackingToken.advancedTo(streamIdOfMessage,messageToReturn.trackingToken());
            trackingToken=newTrackingToken;

            logger.info("Message consumed from {}",streamIdOfMessage);
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
}
