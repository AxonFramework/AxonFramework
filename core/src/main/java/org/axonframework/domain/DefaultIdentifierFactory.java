package org.axonframework.domain;

import java.util.UUID;

/**
 * Default IdentifierFactory implementation that uses generates random <code>java.util.UUID</code> based identifiers.
 * Although the performance of this strategy is not the best out there, it has native supported on all JVMs.
 * <p/>
 * This implementations selects a random identifier out of about 3 x 10<sup>38</sup> possible values, making the chance
 * to get a duplicate incredibly small.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class DefaultIdentifierFactory extends IdentifierFactory {

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation creates identifiers based on pseudo-random UUIDs.
     */
    @Override
    public String generateIdentifier() {
        return UUID.randomUUID().toString();
    }
}
