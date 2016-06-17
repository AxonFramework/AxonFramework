package org.axonframework.commandhandling.distributed.registry;

import java.util.Set;

public interface ServiceRegistryListener<D> {
    public void updateMembers(Set<ServiceMember<D>> members);
}
