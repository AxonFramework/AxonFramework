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
     * The claim timeout is the amount of time a
     * {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessor's} process will wait
     * before it forces a claim of a {@link org.axonframework.eventhandling.TrackingToken}. Thus, if a claim has not
     * been updated for the given {@code claimTimeout}, this process will 'steal' the claim. Defaults to a
     * {@link Duration} of 10 seconds.
     */
    private Duration claimTimeout = Duration.ofSeconds(10);

    /**
     * Gets the claim timeout as {@link Duration}.
     * <p>
     * The claim timeout is the amount of time a
     * {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessor's} process will wait
     * before it forces a claim of a {@link org.axonframework.eventhandling.TrackingToken}. Thus, if a claim has not
     * been updated for the given {@code claimTimeout}, this process will 'steal' the claim. Defaults to a
     * {@link Duration} of 10 seconds.
     * <p>
     *
     * @return the claim timeout as {@link Duration}
     */
    public Duration getClaimTimeout() {
        return claimTimeout;
    }


    /**
     * Sets the claim timeout as {@link Duration}.
     * <p>
     * The claim timeout is the amount of time a
     * {@link org.axonframework.eventhandling.StreamingEventProcessor StreamingEventProcessor's)} process will wait
     * before it forces a claim of a {@link org.axonframework.eventhandling.TrackingToken}. Thus, if a claim has not
     * been updated for the given {@code claimTimeout}, this process will 'steal' the claim.
     * <p>
     *
     * @param claimTimeout the {@link Duration} of the default claim timeout
     */
    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = claimTimeout;
    }
}
