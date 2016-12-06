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
import org.axonframework.common.Assert;
import org.axonframework.common.digest.Digester;

import java.util.*;
import java.util.function.Predicate;

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

    /**
     * Initializes a new {@link ConsistentHash}. To register members use {@link #with(Member, int, Predicate)}.
     */
    public ConsistentHash() {
        hashToMember = Collections.emptySortedMap();
    }

    private ConsistentHash(SortedMap<String, ConsistentHashMember> hashed) {
        hashToMember = hashed;
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
        SortedMap<String, ConsistentHashMember> tailMap = hashToMember.tailMap(hash);
        Iterator<Map.Entry<String, ConsistentHashMember>> tailIterator = tailMap.entrySet().iterator();
        Optional<Member> foundMember = findSuitableMember(commandMessage, tailIterator);
        if (!foundMember.isPresent()) {
            Iterator<Map.Entry<String, ConsistentHashMember>> headIterator =
                    hashToMember.headMap(hash).entrySet().iterator();
            foundMember = findSuitableMember(commandMessage, headIterator);
        }
        return foundMember;
    }

    /**
     * Returns the hash of the given {@code routingKey}. By default this creates a MD5 hash with hex encoding.
     *
     * @param routingKey the routing key to hash
     * @return a hash of the input key
     */
    protected static String hash(String routingKey) {
        return Digester.md5Hex(routingKey);
    }

    private Optional<Member> findSuitableMember(CommandMessage<?> commandMessage,
                                                Iterator<Map.Entry<String, ConsistentHashMember>> iterator) {
        while (iterator.hasNext()) {
            Map.Entry<String, ConsistentHashMember> entry = iterator.next();
            if (entry.getValue().commandFilter.test(commandMessage)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the set of members registered with this consistent hash instance.
     *
     * @return the members of this consistent hash
     */
    public Set<Member> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(hashToMember.values()));
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
    public ConsistentHash with(Member member, int loadFactor, Predicate<? super CommandMessage<?>> commandFilter) {
        Assert.notNull(member, () -> "Member may not be null");

        ConsistentHashMember newMember = new ConsistentHashMember(member, loadFactor, commandFilter);
        if (getMembers().contains(newMember)) {
            return this;
        }

        SortedMap<String, ConsistentHashMember> members = new TreeMap<>(without(member).hashToMember);
        newMember.hashes().forEach(h -> members.put(h, newMember));

        return new ConsistentHash(members);
    }

    /**
     * Deregisters the given {@code member} and returns a new {@link ConsistentHash} with updated memberships.
     *
     * @param member the member to remove from the consistent hash
     * @return a new {@link ConsistentHash} instance with updated memberships
     */
    public ConsistentHash without(Member member) {
        Assert.notNull(member, () -> "Member may not be null");
        SortedMap<String, ConsistentHashMember> newHashes = new TreeMap<>();
        this.hashToMember.forEach((h, v) -> {
            if (!Objects.equals(v.name(), member.name())) {
                newHashes.put(h, v);
            }
        });
        return new ConsistentHash(newHashes);
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

    /**
     * Member implementation used by a {@link ConsistentHash} registry.
     */
    public static class ConsistentHashMember implements Member {

        private final Member member;
        private final int segmentCount;
        private final Predicate<? super CommandMessage<?>> commandFilter;

        private ConsistentHashMember(Member member, int segmentCount,
                                     Predicate<? super CommandMessage<?>> commandFilter) {
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
         * Returns the hashes covered by the member. If the hash of the routing key matches with one of the returned
         * hashes and the member is capable of handling the command then it will be selected as a target for the
         * command.
         *
         * @return the hashes covered by this member
         */
        public Set<String> hashes() {
            Set<String> newHashes = new TreeSet<>();
            for (int t = 0; t < segmentCount; t++) {
                String hash = hash(name() + " #" + t);
                newHashes.add(hash);
            }
            return newHashes;
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
            return member.name() + "("+ segmentCount +")";
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ConsistentHash [");
        Set<ConsistentHashMember> members = new TreeSet<>(Comparator.comparing(ConsistentHashMember::name));
        members.addAll(this.hashToMember.values());
        members.forEach(m -> sb.append(m.toString()).append(","));
        sb.delete(sb.length() - 1, sb.length());
        sb.append("]");
        return sb.toString();
    }
}



