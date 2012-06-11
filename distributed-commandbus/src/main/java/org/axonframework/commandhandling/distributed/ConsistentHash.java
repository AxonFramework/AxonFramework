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

    private static final long serialVersionUID = -586740481616520113L;

    private static final ConsistentHash EMPTY = new ConsistentHash(new TreeMap<String, MemberNode>());
    private final SortedMap<String, MemberNode> hashToMember;

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
        this(new TreeMap<String, MemberNode>());
    }

    private ConsistentHash(SortedMap<String, MemberNode> hashed) {
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
        TreeMap<String, MemberNode> newHashes = new TreeMap<String, MemberNode>(hashToMember);
        Iterator<Map.Entry<String, MemberNode>> iterator = newHashes.entrySet().iterator();
        while (iterator.hasNext()) {
            if (nodeName.equals(iterator.next().getValue().getName())) {
                iterator.remove();
            }
        }
        for (int t = 0; t < segmentCount; t++) {
            String hash = Digester.md5Hex(nodeName + " #" + t);
            newHashes.put(hash, new MemberNode(nodeName, supportedCommandTypes));
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
        SortedMap<String, MemberNode> newHashes = new TreeMap<String, MemberNode>();
        for (Map.Entry<String, MemberNode> entry : hashToMember.entrySet()) {
            if (activeMembers.contains(entry.getValue().getName())) {
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
        SortedMap<String, MemberNode> tailMap = hashToMember.tailMap(hash);
        Iterator<Map.Entry<String, MemberNode>> tailIterator = tailMap.entrySet().iterator();
        MemberNode foundMember = findSuitableMember(commandType, tailIterator);
        if (foundMember == null) {
            // if the tail doesn't have a member, we should start back at the head
            Iterator<Map.Entry<String, MemberNode>> headIterator = hashToMember.headMap(hash).entrySet().iterator();
            foundMember = findSuitableMember(commandType, headIterator);
        }
        return foundMember == null ? null : foundMember.getName();
    }

    private MemberNode findSuitableMember(String commandType, Iterator<Map.Entry<String, MemberNode>> iterator) {
        MemberNode foundMember = null;
        while (iterator.hasNext() && foundMember == null) {
            Map.Entry<String, MemberNode> entry = iterator.next();
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
        Iterator<Map.Entry<String, MemberNode>> iterator = hashToMember.entrySet().iterator();
        if (iterator.hasNext()) {
            w.append("\n");
        }
        while (iterator.hasNext()) {
            Map.Entry<String, MemberNode> entry = iterator.next();
            w.append(entry.getKey())
             .append(" -> ")
             .append(entry.getValue().getName())
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
        out.writeInt(hashToMember.size());
        for (Map.Entry<String, MemberNode> entry : hashToMember.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue().getName());
            out.writeInt(entry.getValue().supportedCommands().size());
            for (String supportedCommand : entry.getValue().supportedCommands()) {
                out.writeUTF(supportedCommand);
            }
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        for (int t = 0; t < size; t++) {
            String key = in.readUTF();
            String memberName = in.readUTF();
            int supportedCommandCount = in.readInt();
            Set<String> supportedCommands = new HashSet<String>(supportedCommandCount);
            for (int c = 0; c < supportedCommandCount; c++) {
                supportedCommands.add(in.readUTF());
            }
            hashToMember.put(key, new MemberNode(memberName, supportedCommands));
        }
    }

    /**
     * @author Allard Buijze
     */
    private static class MemberNode {

        private final String nodeName;
        private final Set<String> supportedCommandTypes;

        public MemberNode(String nodeName, Set<String> supportedCommandTypes) {
            this.nodeName = nodeName;
            this.supportedCommandTypes = new HashSet<String>(supportedCommandTypes);
        }

        public String getName() {
            return nodeName;
        }

        public Set<String> supportedCommands() {
            return Collections.unmodifiableSet(supportedCommandTypes);
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MemberNode that = (MemberNode) o;

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
            int result = nodeName.hashCode();
            result = 31 * result + supportedCommandTypes.hashCode();
            return result;
        }
    }
}
