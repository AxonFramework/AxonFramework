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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Assert;
import org.axonframework.common.digest.Digester;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Component used by command routers to find members capable of handling a given command. Members are selected based on
 * their capabilities and a given routing key. If a ConsistentHash is asked to provide a member for a given routing key
 * it will always returning the same Member as long as none of the memberships have changed.
 * <p>
 * A ConsistentHash is used to route commands targeting the same aggregate to the same member. In that case the
 * aggregate identifier is the routing key.
 */
public class ConsistentHash {

    private final SortedMap<String, ConsistentHashMember> hashToMember;
    private final int modCount;
    private final Function<String, String> hashFunction;
    private final Map<String, ConsistentHashMember> members;

    /**
     * Returns the hash of the given {@code routingKey}. By default this creates a MD5 hash with hex encoding.
     *
     * @param routingKey the routing key to hash
     * @return a hash of the input key
     */
    protected static String hash(String routingKey) {
        return Digester.md5Hex(routingKey);
    }

    /**
     * Initializes a new {@link ConsistentHash}. To register members use {@link #with(Member, int, CommandMessageFilter)}.
     */
    public ConsistentHash() {
        this(ConsistentHash::hash);
    }

    /**
     * Initializes a new {@link ConsistentHash} using the given {@code hashFunction} to calculate positions for each
     * member on the ring. To register members use {@link #with(Member, int, CommandMessageFilter)}.
     *
     * @param hashFunction The hash function to use to calculate each member's positions on the ring
     */
    public ConsistentHash(Function<String, String> hashFunction) {
        hashToMember = Collections.emptySortedMap();
        members = Collections.emptyMap();
        modCount = 0;
        this.hashFunction = hashFunction;
    }

    private ConsistentHash(Map<String, ConsistentHashMember> members,
                           Function<String, String> hashFunction, int modCount) {
        this.hashFunction = hashFunction;
        this.modCount = modCount;
        this.hashToMember = new TreeMap<>();
        this.members = members;
        members.values().forEach(m -> m.hashes().forEach(h -> hashToMember.put(h, m)));
    }

    /**
     * Returns the collection of nodes, represented as {@link ConsistentHashMember}, in the order they would be
     * considered for the given routing key. Whether a CommandMessage would be forwarded to each of the candidates,
     * depends on the Command Filter of each node.
     *
     * @param routingKey The routing key to select ordering
     * @return A collection containing each of the nodes, in the order they would be considered
     */
    public Collection<ConsistentHashMember> getEligibleMembers(String routingKey) {
        String hash = hash(routingKey);
        Collection<ConsistentHashMember> tail = hashToMember.tailMap(hash).values();
        Collection<ConsistentHashMember> head = hashToMember.headMap(hash).values();
        LinkedHashSet<ConsistentHashMember> combined = new LinkedHashSet<>(tail);
        combined.addAll(head);
        return combined;
    }

    /**
     * Returns the member instance to which the given {@code message} should be routed. If no suitable member could be
     * found an empty Optional is returned.
     *
     * @param routingKey     the routing that should be used to select a member
     * @param commandMessage the command message to find a member for
     * @return the member that should handle the message or an empty Optional if no suitable member was found
     */
    public Optional<Member> getMember(String routingKey, CommandMessage<?> commandMessage) {
        String hash = hash(routingKey);
        Optional<Member> foundMember = findSuitableMember(commandMessage, hashToMember.tailMap(hash).values());
        if (!foundMember.isPresent()) {
            foundMember = findSuitableMember(commandMessage, hashToMember.headMap(hash).values());
        }
        return foundMember;
    }

    private Optional<Member> findSuitableMember(CommandMessage<?> commandMessage,
                                                Collection<ConsistentHashMember> members) {
        return members.stream()
                .filter(member -> member.commandFilter.matches(commandMessage))
                .map(Member.class::cast)
                .findAny();
    }

    /**
     * Returns the set of members registered with this consistent hash instance.
     *
     * @return the members of this consistent hash
     */
    public Set<Member> getMembers() {
        return new HashSet<>(members.values());
    }

    /**
     * Registers the given {@code member} with given {@code loadFactor} and {@code commandFilter} if it is not
     * already contained in the {@link ConsistentHash}. It will return the current ConsistentHash if the addition is
     * a duplicate and returns a new ConsistentHash with updated memberships if it is not.
     * <p>
     * The relative loadFactor of the member determines the likelihood of being selected as a destination for a command.
     *
     * @param member        the member to register
     * @param loadFactor    the load factor of the new member
     * @param commandFilter filter describing which commands can be handled by the given member
     * @return a new {@link ConsistentHash} instance with updated memberships
     */
    public ConsistentHash with(Member member, int loadFactor, CommandMessageFilter commandFilter) {
        Assert.notNull(member, () -> "Member may not be null");

        ConsistentHashMember newMember = new ConsistentHashMember(member, loadFactor, commandFilter);
        if (members.containsKey(member.name()) && newMember.equals(members.get(member.name()))) {
            return this;
        }

        Map<String, ConsistentHashMember> newMembers = new TreeMap<>(members);
        newMembers.put(member.name(), newMember);

        return new ConsistentHash(newMembers, hashFunction, modCount + 1);
    }

    /**
     * Deregisters the given {@code member} and returns a new {@link ConsistentHash} with updated memberships.
     *
     * @param member the member to remove from the consistent hash
     * @return a new {@link ConsistentHash} instance with updated memberships
     */
    public ConsistentHash without(Member member) {
        Assert.notNull(member, () -> "Member may not be null");
        if (!members.containsKey(member.name())) {
            return this;
        }

        Map<String, ConsistentHashMember> newMembers = new TreeMap<>(members);
        newMembers.remove(member.name());
        return new ConsistentHash(newMembers, hashFunction, modCount + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConsistentHash that = (ConsistentHash) o;
        return Objects.equals(hashToMember, that.hashToMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashToMember);
    }

    @Override
    public String toString() {
        String join = this.members.values().stream()
                .map(ConsistentHashMember::toString)
                .collect(Collectors.joining(","));
        return "ConsistentHash ["+join+"]";
    }

    /**
     * Returns the version of this consistent hash instance. The version is increased by one each time a change is made.
     *
     * @return the version of this consistent hash instance
     */
    public int version() {
        return modCount;
    }

    /**
     * Member implementation used by a {@link ConsistentHash} registry.
     */
    public static class ConsistentHashMember implements Member {

        private final Member member;
        private final int segmentCount;
        private final CommandMessageFilter commandFilter;

        private ConsistentHashMember(Member member, int segmentCount,
                                     CommandMessageFilter commandFilter) {
            if (member instanceof ConsistentHashMember) {
                this.member = ((ConsistentHashMember) member).member;
            } else {
                this.member = member;
            }
            this.segmentCount = segmentCount;
            this.commandFilter = commandFilter;
        }

        @Override
        public String name() {
            return member.name();
        }

        @Override
        public boolean local() {
            return member.local();
        }

        @Override
        public void suspect() {
            member.suspect();
        }

        /**
         * Returns this member's segment count which relates to the relative load factor of the member.
         *
         * @return the member's segment count
         */
        public int segmentCount() {
            return segmentCount;
        }

        /**
         * Returns this member's filter describing the commands it supports
         *
         * @return the filter that describes this member's supported commands
         */
        public CommandMessageFilter getCommandFilter() {
            return commandFilter;
        }

        /**
         * Returns the hashes covered by the member. If the hash of the routing key matches with one of the returned
         * hashes and the member is capable of handling the command then it will be selected as a target for the
         * command.
         *
         * @return the hashes covered by this member
         */
        public Set<String> hashes() {
            return IntStream.range(0, segmentCount)
                            .mapToObj(i -> hash(name() + " #" + i))
                            .collect(Collectors.toSet());
        }

        @Override
        public <T> Optional<T> getConnectionEndpoint(Class<T> protocol) {
            return member.getConnectionEndpoint(protocol);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConsistentHashMember that = (ConsistentHashMember) o;
            return segmentCount == that.segmentCount &&
                    Objects.equals(member, that.member) &&
                    Objects.equals(commandFilter, that.commandFilter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(member, segmentCount, commandFilter);
        }

        @Override
        public String toString() {
            return member.name() + "(" + segmentCount + ")";
        }
    }
}



