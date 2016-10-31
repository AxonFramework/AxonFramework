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

public class ConsistentHash {

    private final SortedMap<String, ConsistentHashMember> hashToMember;

    public ConsistentHash() {
        hashToMember = Collections.emptySortedMap();
    }

    private ConsistentHash(SortedMap<String, ConsistentHashMember> hashed) {
        hashToMember = hashed;
    }

    public Optional<Member> getMember(String routingKey, CommandMessage<?> commandMessage) {
        String hash = hash(routingKey);
        SortedMap<String, ConsistentHashMember> tailMap = hashToMember.tailMap(hash);
        Iterator<Map.Entry<String, ConsistentHashMember>> tailIterator = tailMap.entrySet().iterator();
        Optional<Member> foundMember = findSuitableMember(commandMessage, tailIterator);
        if (!foundMember.isPresent()) {
            Iterator<Map.Entry<String, ConsistentHashMember>> headIterator = hashToMember.headMap(hash).entrySet().iterator();
            foundMember = findSuitableMember(commandMessage, headIterator);
        }
        return foundMember;
    }

    protected static String hash(String routingKey) {
        return Digester.md5Hex(routingKey);
    }

    private Optional<Member> findSuitableMember(CommandMessage<?> commandMessage, Iterator<Map.Entry<String, ConsistentHashMember>> iterator) {
        while (iterator.hasNext()) {
            Map.Entry<String, ConsistentHashMember> entry = iterator.next();
            if (entry.getValue().commandFilter.test(commandMessage)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public Set<Member> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(hashToMember.values()));
    }

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

    public ConsistentHash with(Member member, int loadFactor, Predicate<? super CommandMessage<?>> commandFilter) {
        Assert.notNull(member, () -> "Member may not be null");
        SortedMap<String, ConsistentHashMember> members = new TreeMap<>(without(member).hashToMember);

        ConsistentHashMember newMember = new ConsistentHashMember(member, loadFactor, commandFilter);
        newMember.hashes().forEach(h -> members.put(h, newMember));

        return new ConsistentHash(members);
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

    public static class ConsistentHashMember implements Member {
        private final Member member;
        private final int segmentCount;
        private final Predicate<? super CommandMessage<?>> commandFilter;

        public ConsistentHashMember(Member member, int segmentCount, Predicate<? super CommandMessage<?>> commandFilter) {
            if (member instanceof ConsistentHashMember) {
                this.member = ((ConsistentHashMember) member).member;
            }else {
                this.member = member;
            }
            this.segmentCount = segmentCount;
            this.commandFilter = commandFilter;
        }

        public String name() {
            return member.name();
        }

        @Override
        public void suspect() {
            member.suspect();
        }

        public int segmentCount() {
            return segmentCount;
        }

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
    }
}



