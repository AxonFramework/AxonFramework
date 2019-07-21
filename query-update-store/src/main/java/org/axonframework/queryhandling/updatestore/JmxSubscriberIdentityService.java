package org.axonframework.queryhandling.updatestore;

import java.lang.management.ManagementFactory;

public class JmxSubscriberIdentityService implements SubscriberIdentityService {

    /**
     * @see org.axonframework.eventhandling.tokenstore.jpa.JpaTokenStore.Builder JpaTokenStore (Builder) nodeId
     */
    @Override
    public String getSubscriberIdentify() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }
}
