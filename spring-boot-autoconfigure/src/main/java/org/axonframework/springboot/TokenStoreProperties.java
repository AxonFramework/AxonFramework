package org.axonframework.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Properties describing the settings for the default
 * {@link org.axonframework.eventhandling.tokenstore.TokenStore Token Store}.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@ConfigurationProperties("axon.eventhandling.tokenstore")
public class TokenStoreProperties {

    /**
     * The claim timeout is the amount of time a {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessor's)
     * process will wait before it forces a claim of a {@link org.axonframework.eventhandling.TrackingToken}.
     * Thus, if a claim has not been updated for the given {@code claimTimeout}, this process will 'steal' the claim.
     * Defaults to a {@link Duration} of 10 seconds.
     */
    private Duration claimTimeout = Duration.ofSeconds(10);

    /**
     * Gets the claim timeout as {@link Duration}. 
     * <p>
     * The claim timeout is the amount of time a {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessor's) 
     * process will wait before it forces a claim of a {@link org.axonframework.eventhandling.TrackingToken}. 
     * Thus, if a claim has not been updated for the given {@code claimTimeout}, this process will 'steal' the claim. 
     * Defaults to a {@link Duration} of 10 seconds.
     * <p>
     * @return the claim timeout as {@link Duration}
     */
    public Duration getClaimTimeout() {
        return claimTimeout;
    }


    /**
     * Sets the claim timeout as {@link Duration}.
     * <p>
     * The claim timeout is the amount of time a {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessor's) 
     * process will wait before it forces a claim of a {@link org.axonframework.eventhandling.TrackingToken}. 
     * Thus, if a claim has not been updated for the given {@code claimTimeout}, this process will 'steal' the claim. 
     * <p>
     * @param claimTimeout the {@link Duration} of the default claim timeout
     */
    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = claimTimeout;
    }
}
