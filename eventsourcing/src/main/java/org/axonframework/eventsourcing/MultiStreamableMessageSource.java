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
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private final Comparator<TrackedSourcedEventMessage<?>> comparator;
    private TrackedEventMessage<?> peekedMessage;
    private String longPollingSource;

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

        //ensure longPollingSource is last item in the LinkedHashMap
        if(! builder.longPollingSource.equals("")) {
            this.longPollingSource = builder.longPollingSource;
            StreamableMessageSource sourcreToReAdd = this.eventStreams.remove(longPollingSource);
            this.eventStreams.put(longPollingSource, sourcreToReAdd);
        } else {
            this.longPollingSource = builder.lastSourceInserted;
        }

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
            HashMap<String, TrackedSourcedEventMessage> candidateMessagesToReturn = new HashMap<>();

            for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                Optional<TrackedEventMessage<?>> currentPeekedMessage = singleMessageSource.getValue().peek();
                currentPeekedMessage.ifPresent(
                        trackedEventMessage ->
                                candidateMessagesToReturn.put(singleMessageSource.getKey(),
                                                              new GenericTrackedSourcedEventMessage(singleMessageSource.getKey(), trackedEventMessage)));
            }

            //select message to return from candidates
            final Optional<Map.Entry<String, TrackedSourcedEventMessage>> chosenMessage =
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
         * @param timeout the maximum number of time units to wait for messages to become available. This time
         *                is spent polling on one particular stream (specified by {@link Builder#configureLongPollingSource(String)}
         *                or by default to last stream provide to {@link Builder#addMessageSource(String, StreamableMessageSource)}.
         *                Between polling on this stream, {@link #hasNextAvailable()} is called on the others to check
         *                if a message is immediately consumable on the other streams.
         * @param unit    the time unit for the timeout.
         * @return true if any stream has an available message. Otherwise false.
         * @throws InterruptedException when the thread is interrupted before the indicated time is up.
         */
        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            int longPollTime = timeout/10;

            while (System.currentTimeMillis() < deadline) {
                Iterator<Map.Entry<String, BlockingStream<TrackedEventMessage<?>>>> it = messageSources.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> current = it.next();

                    if (it.hasNext() != false) {
                        if (current.getValue().hasNextAvailable()) return true;
                    } else {
                        if (current.getValue().hasNextAvailable((int) Math.min(longPollTime, deadline - System.currentTimeMillis()), unit)) return true;
                    }
                }
            }

            return false;
        }

        /**
         * Checks each stream to see if a message is available. If more than one stream has a message it decides which
         * message to return using the {@link #comparator}.
         * @return selected message using {@link #comparator}.
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

            HashMap<String, TrackedSourcedEventMessage> candidateMessagesToReturn = new HashMap<>();

            while(candidateMessagesToReturn.size() == 0) {
                for (Map.Entry<String, BlockingStream<TrackedEventMessage<?>>> singleMessageSource : messageSources.entrySet()) {
                    Optional<TrackedEventMessage<?>> currentPeekedMessage = singleMessageSource.getValue().peek();
                    currentPeekedMessage.ifPresent(trackedEventMessage ->
                                                           candidateMessagesToReturn.put(singleMessageSource.getKey(),new GenericTrackedSourcedEventMessage(singleMessageSource.getKey(), trackedEventMessage)));
                }
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

        private Comparator<TrackedSourcedEventMessage<?>> comparator = Comparator.comparing(EventMessage::getTimestamp);
        private Map<String,StreamableMessageSource<TrackedEventMessage<?>>> messageSourceMap = new LinkedHashMap<>();
        private String longPollingSource = "";
        private String lastSourceInserted = "";

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
            lastSourceInserted = messageSourceName;
            return this;
        }

        /**
         * Overrides the default comparator (which returns the oldest event available).
         * @param comparator the comparator to use when deciding on which message to return.
         * @return the current Builder instance, for fluent interfacing.
         */
        public Builder withComparator(Comparator<TrackedSourcedEventMessage<?>> comparator){
            this.comparator = comparator;
            return this;
        }


        /**
         * Select the message source which is most suitable for long polling. To prevent excessive polling on all sources
         * it is preferable to do the majority of polling on a single source. All other streams will be checked first
         * using {@link BlockingStream#hasNextAvailable()} before {@link BlockingStream#hasNextAvailable(int, TimeUnit)} is
         * called on the source chosen for long polling. This is then repeated multiple times to increase the chance of
         * successfully finding a message before the timeout. If no particular source is configured, long polling with be
         * done on the last configured source.
         *  whilst other streams will be using {@link BlockingStream#hasNextAvailable()}
         *
         * @param longPollingSource
         * @return
         */
        public Builder configureLongPollingSource(String longPollingSource){
            Assert.isTrue(messageSourceMap.containsKey(longPollingSource), () -> "Current configuration does not contain this message source");

            this.longPollingSource = longPollingSource;
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
