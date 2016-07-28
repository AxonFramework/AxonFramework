/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Service registry with a static member list
 */
public class StaticCommandRouter implements CommandRouter {

    private final Set<ServiceMember<?>> members;
    private final ConsistentHash consistentHash;
    private final RoutingStrategy routingStrategy;

    public StaticCommandRouter(RoutingStrategy routingStrategy, ServiceMember<?>... members) {
        this.members = new HashSet<>(Arrays.asList(members));
        this.routingStrategy = routingStrategy;
        ConsistentHash ch = new ConsistentHash();
        for (ServiceMember<?> member : members) {
            ch = ch.with(member, member.loadFactor(), member.commandFilter());
        }
        consistentHash = ch;
    }

    public Set<ServiceMember<?>> getMembers() {
        return members;
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> message) {
        return consistentHash.getMember(routingStrategy.getRoutingKey(message), message);
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<CommandMessage<?>> commandFilter) {
        // it's static
    }

    public static class ServiceMember<T> extends SimpleMember<T> {

        private final int loadFactor;
        private final Predicate<CommandMessage<?>> commandFilter;

        public ServiceMember(String name, T endpoint, int loadFactor, Predicate<CommandMessage<?>> commandFilter) {
            super(name, endpoint, (s) -> {});
            this.loadFactor = loadFactor;
            this.commandFilter = commandFilter;
        }

        public int loadFactor() {
            return loadFactor;
        }

        public Predicate<CommandMessage<?>> commandFilter() {
            return commandFilter;
        }
    }
}
