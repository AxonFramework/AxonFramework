package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

record GapAwareTrackingTokenOperations(
        int gapTimeout,
        Logger logger
) {

    GapAwareTrackingToken withGapsCleaned(GapAwareTrackingToken token, List<Object[]> indexToTimestamp) {
        Instant gapTimeoutThreshold = gapTimeoutThreshold();
        GapAwareTrackingToken cleanedToken = token;
        for (Object[] result : indexToTimestamp) {
            try {
                Instant timestamp = DateTimeUtils.parseInstant(result[1].toString());
                long sequenceNumber = (long) result[0];
                if (cleanedToken.getGaps().contains(sequenceNumber) || timestamp.isAfter(gapTimeoutThreshold)) {
                    // filled a gap or found an entry that is too recent. Should not continue cleaning up
                    return cleanedToken;
                }
                if (cleanedToken.getGaps().contains(sequenceNumber - 1)) {
                    cleanedToken = cleanedToken.withGapsTruncatedAt(sequenceNumber);
                }
            } catch (DateTimeParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.info("Unable to parse timestamp ('{}') to clean old gaps. Trying to proceed. ",
                                e.getParsedString(), e);
                } else {
                    logger.info("Unable to parse timestamp ('{}') to clean old gaps. Trying to proceed. " +
                                        "Exception message: {}. (enable debug logging for full stack trace)",
                                e.getParsedString(), e.getMessage());
                }
            }
        }
        return cleanedToken;
    }

    GapAwareTrackingToken assertGapAwareTrackingToken(TrackingToken trackingToken) {
        if (trackingToken instanceof GapAwareTrackingToken gapAwareTrackingToken) {
            return gapAwareTrackingToken;
        } else {
            throw new IllegalArgumentException(
                    "Tracking Token is not of expected type. Must be GapAwareTrackingToken. Is: "
                            + trackingToken.getClass().getName());
        }
    }


    Instant gapTimeoutThreshold() {
        return GenericEventMessage.clock.instant().minus(gapTimeout, ChronoUnit.MILLIS);
    }
}
