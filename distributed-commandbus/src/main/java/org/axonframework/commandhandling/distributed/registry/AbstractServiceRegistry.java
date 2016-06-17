package org.axonframework.commandhandling.distributed.registry;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public abstract class AbstractServiceRegistry<D> implements ServiceRegistry<D> {
    private final Set<ServiceRegistryListener<D>> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void addListener(ServiceRegistryListener<D> serviceRegistryListener) {
        listeners.add(serviceRegistryListener);
    }

    public void update(Set<ServiceMember<D>> serviceMembers) {
        listeners.forEach(listener -> listener.updateMembers(serviceMembers));
    }
}
