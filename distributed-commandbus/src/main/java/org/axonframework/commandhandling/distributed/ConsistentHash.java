package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.registry.ServiceMember;
import org.axonframework.common.digest.Digester;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ConsistentHash<T extends ServiceMember> {
    private final SortedMap<String, Member<T>> hashToMember;

    public ConsistentHash() {
        hashToMember = Collections.emptySortedMap();
    }

    private ConsistentHash(SortedMap<String, Member<T>> hashed) {
        hashToMember = hashed;
    }

    public T getMember(String routingKey, CommandMessage commandMessage) {
        SortedMap<String, Member<T>> tailMap = hashToMember.tailMap(routingKey);
        Iterator<Map.Entry<String, Member<T>>> tailIterator = tailMap.entrySet().iterator();
        Member<T> foundMember = findSuitableMember(commandMessage, tailIterator);
        if (foundMember == null) {
            Iterator<Map.Entry<String, Member<T>>> headIterator = hashToMember.headMap(routingKey).entrySet().iterator();
            foundMember = findSuitableMember(commandMessage, headIterator);
        }
        return foundMember == null ? null : foundMember.node();
    }

    private Member<T> findSuitableMember(CommandMessage commandMessage, Iterator<Map.Entry<String, Member<T>>> iterator) {
        while (iterator.hasNext()) {
            Map.Entry<String, Member<T>> entry = iterator.next();
            if (entry.getValue().commandFilter.test(commandMessage)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Set<T> getMembers() {
        return Collections.unmodifiableSet(hashToMember.values().stream().map(member -> member.node).collect(Collectors.toSet()));
    }

    public ConsistentHash<T> rebuildWith(Set<T> members) {
        SortedMap<String, Member<T>> sortedMembers = new TreeMap<>();
        for (T member : members) {
            Member<T> ringMember = new Member<T>(member, member.getLoadFactor(), member.getCommandFilter());
            ringMember.hashes().forEach(hash -> sortedMembers.put(hash, ringMember));
        }
        return new ConsistentHash<>(sortedMembers);
    }

    public ConsistentHash<T> rebuildWith(T member) {
        Map<Object, T> members = getMembers().stream()
                .collect(Collectors.toMap(ServiceMember::getIdentifier, Function.identity()));

        members.put(member.getIdentifier(), member);

        return rebuildWith(new HashSet<>(members.values()));
    }

    public static class Member<T> {
        private final T node;
        private final Predicate<CommandMessage> commandFilter;
        private final Set<String> hashes;

        public Member(T node, int segmentCount, Predicate<CommandMessage> commandFilter) {
            this.node = node;
            this.commandFilter = commandFilter;
            Set<String> newHashes = new TreeSet<>();
            for (int t = 0; t < segmentCount; t++) {
                String hash = Digester.md5Hex(name() + " #" + t);
                newHashes.add(hash);
            }
            this.hashes = Collections.unmodifiableSet(newHashes);
        }

        public String name() {
            return node.toString();
        }

        public T node() {
            return node;
        }

        public Set<String> hashes() {
            return hashes;
        }
    }
}



