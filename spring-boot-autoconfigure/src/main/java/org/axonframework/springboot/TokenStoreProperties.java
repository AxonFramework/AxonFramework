package org.axonframework.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Properties describing the settings for the default Token Store.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@ConfigurationProperties("axon.tokenstore")
public class TokenStoreProperties {

    private Duration claimTimeout = Duration.ofSeconds(10);

    /**
     * Gets the claim timeout as {@link Duration}. This is the amount of time this process will wait after which this
     * process will force a claim of a {@link org.axonframework.eventhandling.TrackingToken}. Thus, if a claim has not
     * been updated for the given {@code claimTimeout}, this process will 'steal' the claim. Defaults to a duration of
     * 10 seconds.
     *
     * @return the clam timeout as {@link Duration}
     */
    public Duration getClaimTimeout() {
        return claimTimeout;
    }


    /**
     * Sets the claim timeout as {@link Duration}. This is the amount of time this process will wait after which this
     * process will force a claim of a {@link org.axonframework.eventhandling.TrackingToken}. Thus, if a claim has not
     * been updated for the given {@code claimTimeout}, this process will 'steal' the claim.
     *
     * @param claimTimeout the {@link Duration} of the default claim timeout
     */
    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = claimTimeout;
    }
}
