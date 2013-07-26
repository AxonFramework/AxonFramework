/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.digest.Digester;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Basic implementation of a Consistent Hashing algorithm, using the MD5 algorithm to build the hash values for given
 * keys and node names. It contains some basic operation to add nodes and remove nodes given a set of known remaining
 * members.
 * <p/>
 * Each node contains a Set of supported Commands (as a set of the fully qualified names of payload types). When
 * performing a lookup for a given command, only nodes that support the payload type of the command are eligible.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ConsistentHash implements Externalizable {

    private static final long serialVersionUID = 799974496899291960L;

    private static final ConsistentHash EMPTY = new ConsistentHash(new TreeMap<String, Member>());
    private final SortedMap<String, Member> hashToMember;

    /**
     * Returns an instance of an empty Ring, which can be used to add members.
     *
     * @return an empty ConsistentHash ring.
     */
    public static ConsistentHash emptyRing() {
        return EMPTY;
    }

    /**
     * Initializes an empty hash.
     * <p/>
     * This constructor is required for serialization. Instead of using this constructor, use {@link #emptyRing()} to
     * obtain an instance.
     */
    @SuppressWarnings("UnusedDeclaration")
    public ConsistentHash() {
        this(new TreeMap<String, Member>());
    }

    private ConsistentHash(SortedMap<String, Member> hashed) {
        hashToMember = hashed;
    }

    /**
     * Returns a ConsistentHash with the given additional <code>nodeName</code>, which is given
     * <code>segmentCount</code> segments on the ring. A registration of a node will completely override any previous
     * registration known for that node.
     *
     * @param nodeName              The name of the node to add. This will be used to compute the segments
     * @param segmentCount          The number of segments to add the given node
     * @param supportedCommandTypes The fully qualified names of command (payload) types this node supports
     * @return a ConsistentHash with the given additional node
     */
    public ConsistentHash withAdditionalNode(String nodeName, int segmentCount, Set<String> supportedCommandTypes) {
        TreeMap<String, Member> newHashes = new TreeMap<String, Member>(hashToMember);
        Iterator<Map.Entry<String, Member>> iterator = newHashes.entrySet().iterator();
        while (iterator.hasNext()) {
            if (nodeName.equals(iterator.next().getValue().name())) {
                iterator.remove();
            }
        }
        Member node = new Member(nodeName, segmentCount, supportedCommandTypes);
        for (String key : node.hashes()) {
            newHashes.put(key, node);
        }
        return new ConsistentHash(newHashes);
    }

    /**
     * Returns a ConsistentHash instance where only segments leading to the given <code>nodes</code> are available.
     * Each
     * lookup will always result in one of the given <code>nodes</code>.
     *
     * @param nodes The nodes to keep in the consistent hash
     * @return a ConsistentHash instance where only segments leading to the given <code>nodes</code> are available
     */
    public ConsistentHash withExclusively(Collection<String> nodes) {
        Set<String> activeMembers = new HashSet<String>(nodes);
        SortedMap<String, Member> newHashes = new TreeMap<String, Member>();
        for (Map.Entry<String, Member> entry : hashToMember.entrySet()) {
            if (activeMembers.contains(entry.getValue().name())) {
                newHashes.put(entry.getKey(), entry.getValue());
            }
        }
        return new ConsistentHash(newHashes);
    }

    /**
     * Returns the member for the given <code>item</code>, that supports given <code>commandType</code>. If no such
     * member is available, this method returns <code>null</code>.
     *
     * @param item        The item to find a node name for
     * @param commandType The type of command the member must support
     * @return The node name for the given <code>item</code>, or <code>null</code> if not found
     */
    public String getMember(String item, String commandType) {
        String hash = Digester.md5Hex(item);
        SortedMap<String, Member> tailMap = hashToMember.tailMap(hash);
        Iterator<Map.Entry<String, Member>> tailIterator = tailMap.entrySet().iterator();
        Member foundMember = findSuitableMember(commandType, tailIterator);
        if (foundMember == null) {
            // if the tail doesn't have a member, we should start back at the head
            Iterator<Map.Entry<String, Member>> headIterator = hashToMember.headMap(hash).entrySet().iterator();
            foundMember = findSuitableMember(commandType, headIterator);
        }
        return foundMember == null ? null : foundMember.name();
    }

    private Member findSuitableMember(String commandType, Iterator<Map.Entry<String, Member>> iterator) {
        Member foundMember = null;
        while (iterator.hasNext() && foundMember == null) {
            Map.Entry<String, Member> entry = iterator.next();
            if (entry.getValue().supportedCommands().contains(commandType)) {
                foundMember = entry.getValue();
            }
        }
        return foundMember;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConsistentHash ring = (ConsistentHash) o;

        return hashToMember.equals(ring.hashToMember);
    }

    @Override
    public int hashCode() {
        return hashToMember.hashCode();
    }

    @Override
    public String toString() {
        StringWriter w = new StringWriter();
        w.append("ConsistentHash: {");
        Iterator<Map.Entry<String, Member>> iterator = hashToMember.entrySet().iterator();
        if (iterator.hasNext()) {
            w.append("\n");
        }
        while (iterator.hasNext()) {
            Map.Entry<String, Member> entry = iterator.next();
            w.append(entry.getKey())
             .append(" -> ")
             .append(entry.getValue().name())
             .append("(");
            Iterator<String> commandIterator = entry.getValue().supportedCommands().iterator();
            while (commandIterator.hasNext()) {
                w.append(commandIterator.next());
                if (commandIterator.hasNext()) {
                    w.append(", ");
                }
            }
            w.append(")");
            if (iterator.hasNext()) {
                w.append(", ");
            }
            w.append("\n");
        }
        w.append("}");
        return w.toString();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        Set<Member> members = new HashSet<Member>(hashToMember.values());
        out.writeInt(members.size());
        for (Member node : members) {
            out.writeUTF(node.name());
            out.writeInt(node.segmentCount());
            out.writeInt(node.supportedCommands().size());
            for (String supportedCommand : node.supportedCommands()) {
                out.writeUTF(supportedCommand);
            }
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        for (int t = 0; t < size; t++) {
            String memberName = in.readUTF();
            int loadFactor = in.readInt();
            int supportedCommandCount = in.readInt();
            Set<String> supportedCommands = new HashSet<String>(supportedCommandCount);
            for (int c = 0; c < supportedCommandCount; c++) {
                supportedCommands.add(in.readUTF());
            }

            Member node = new Member(memberName, loadFactor, supportedCommands);
            for (String key : node.hashes()) {
                hashToMember.put(key, node);
            }
        }
    }

    /**
     * Returns the set of members part of this hash ring.
     *
     * @return the set of members part of this hash ring
     */
    public Set<Member> getMembers() {
        return Collections.unmodifiableSet(new HashSet<Member>(hashToMember.values()));
    }

    /**
     * Represents a member in a consistently hashed cluster. A member is identified by its name, supports a number of
     * commands and can have any number of segments (a.k.a buckets).
     * <p/>
     * Note that a single member may be presented by multiple {@code Member} instances if the number of segments
     * differs per supported command type.
     *
     * @author Allard Buijze
     */
    public static class Member {

        private final String nodeName;
        private final Set<String> supportedCommandTypes;
        private final Set<String> hashes;

        /**
         * Constructs a new member with given <code>nodeName</code>, <code>segmentCount</code> supporting given
         * <code>supportedCommandTypes</code>.
         *
         * @param nodeName              The name of the node
         * @param segmentCount          The number of segments the node should have on the hash ring
         * @param supportedCommandTypes The commands supported by this node
         */
        public Member(String nodeName, int segmentCount, Set<String> supportedCommandTypes) {
            this.nodeName = nodeName;
            this.supportedCommandTypes = Collections.unmodifiableSet(new HashSet<String>(supportedCommandTypes));
            Set<String> newHashes = new TreeSet<String>();
            for (int t = 0; t < segmentCount; t++) {
                String hash = Digester.md5Hex(nodeName + " #" + t);
                newHashes.add(hash);
            }
            this.hashes = Collections.unmodifiableSet(newHashes);
        }

        /**
         * Returns the name of this member. Members are typically uniquely identified by their name.
         * <p/>
         * Note that a single member may be presented by multiple {@code Member} instances if the number of segments
         * differs per supported command type. Therefore, the name should not be considered an absolutely unique value.
         *
         * @return the name of this member
         */
        public String name() {
            return nodeName;
        }

        /**
         * Returns the set of commands supported by this member.
         *
         * @return the set of commands supported by this member
         */
        public Set<String> supportedCommands() {
            return supportedCommandTypes;
        }

        /**
         * Returns the number of segments this member has on the consistent hash ring. Depending on the spread of the
         * hashing algorithm used (default MD5), this number is an indication of the load of this node compared to
         * other nodes.
         *
         * @return the number of segments this member has on the consistent hash ring
         */
        public int segmentCount() {
            return hashes.size();
        }

        /**
         * Returns the hash values assigned to this member. These values are used to locate the member to handle any
         * given command.
         *
         * @return the hash values assigned to this member
         */
        public Set<String> hashes() {
            return hashes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Member that = (Member) o;

            if (!hashes.equals(that.hashes)) {
                return false;
            }
            if (!nodeName.equals(that.nodeName)) {
                return false;
            }
            if (!supportedCommandTypes.equals(that.supportedCommandTypes)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return nodeName.hashCode();
        }
    }
}
