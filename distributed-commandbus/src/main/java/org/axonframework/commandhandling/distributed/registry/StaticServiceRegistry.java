package org.axonframework.commandhandling.distributed.registry;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Service registry with a static member list
 */
public class StaticServiceRegistry<D> extends AbstractServiceRegistry<D> {
    public static final int LOAD_FACTOR = 100;
    private final Set<ServiceMember<D>> members;

    public StaticServiceRegistry(Set<D> members) {
        this.members = members.stream().map(this::createServiceMember).collect(Collectors.toSet());
        update(this.members);
    }

    public Set<ServiceMember<D>> getMembers() {
        return members;
    }

    protected ServiceMember<D> createServiceMember(D member) {
        return new ServiceMember<D>(member, AcceptAll.INSTANCE, LOAD_FACTOR);
    }

    @Override
    public void addListener(ServiceRegistryListener<D> serviceRegistryListener) {
        super.addListener(serviceRegistryListener);
        serviceRegistryListener.updateMembers(this.members);
    }

    @Override
    public void publish(D member, int loadFactor, Predicate<CommandMessage> commandFilter) {
        //We ignore this
    }

    @Override
    public void unpublish(D member) {
        //We ignore this too
    }
}
