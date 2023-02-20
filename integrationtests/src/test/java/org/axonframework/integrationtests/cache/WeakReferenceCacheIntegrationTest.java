package org.axonframework.integrationtests.cache;

import org.axonframework.common.caching.Cache;
import org.axonframework.common.caching.WeakReferenceCache;

/**
 * {@link WeakReferenceCache} specific implementation of the {@link CachingIntegrationTestSuite}.
 *
 * @author Steven van Beelen
 */
class WeakReferenceCacheIntegrationTest extends CachingIntegrationTestSuite {

    @Override
    public Cache buildCache(String name) {
        return new WeakReferenceCache();
    }
}
