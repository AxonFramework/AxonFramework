package org.axonframework.commandhandling.distributed.registry;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public interface ServiceRegistry<D> {
    void addListener(ServiceRegistryListener<D> serviceRegistryListener);
    void update(Set<ServiceMember<D>> serviceMembers);
    void publish(D localAddress, int loadFactor, Predicate<CommandMessage> commandFilter)
            throws ServiceRegistryException;
    void unpublish(D localAddress) throws ServiceRegistryException;
}
