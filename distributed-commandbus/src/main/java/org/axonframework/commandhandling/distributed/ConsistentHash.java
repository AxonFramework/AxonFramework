package org.axonframework.commandhandling.distributed;

import org.axonframework.common.digest.Digester;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Basic implementation of a Consistent Hashing algorithm, using the MD5 algorithm to build the hash values for given
 * keys and node names. It contains some basic operation to add nodes and remove nodes given a set of known reamining
 * members.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ConsistentHash implements Externalizable {

    private static final long serialVersionUID = -586740481616520113L;

    private static final ConsistentHash EMPTY = new ConsistentHash(new TreeMap<String, String>());

    private final SortedMap<String, String> hashToMemberName;

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
        this(new TreeMap<String, String>());
    }

    private ConsistentHash(SortedMap<String, String> hashed) {
        hashToMemberName = hashed;
    }

    /**
     * Returns a ConsistentHash with the given additional <code>nodeName</code>, which is given
     * <code>segmentCount</code> segments on the ring.
     *
     * @param nodeName     The name of the node to add. This will be used to compute the segments
     * @param segmentCount The number of segments to add the given node
     * @return a ConsistentHash with the given additional node
     */
    public ConsistentHash withAdditionalNode(String nodeName, int segmentCount) {
        TreeMap<String, String> newHashes = new TreeMap<String, String>(hashToMemberName);
        Iterator<Map.Entry<String, String>> iterator = newHashes.entrySet().iterator();
        while (iterator.hasNext()) {
            if (nodeName.equals(iterator.next().getValue())) {
                iterator.remove();
            }
        }
        for (int t = 0; t < segmentCount; t++) {
            String hash = Digester.md5Hex(nodeName + " #" + t);
            newHashes.put(hash, nodeName);
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
    public ConsistentHash withExclusively(List<String> nodes) {
        Set<String> activeMembers = new HashSet<String>(nodes);
        SortedMap<String, String> newHashes = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : hashToMemberName.entrySet()) {
            if (activeMembers.contains(entry.getValue())) {
                newHashes.put(entry.getKey(), entry.getValue());
            }
        }
        return new ConsistentHash(newHashes);
    }

    /**
     * Returns the name of the node for the given <code>item</code>.
     *
     * @param item The item to find a node name for
     * @return The node name for the given <code>item</code>
     */
    public String getNodeName(String item) {
        String hash = Digester.md5Hex(item);
        if (hashToMemberName.containsKey(hash)) {
            return hashToMemberName.get(hash);
        } else {
            SortedMap<String, String> tailMap = hashToMemberName.tailMap(hash);
            if (tailMap.isEmpty()) {
                return hashToMemberName.get(hashToMemberName.firstKey());
            } else {
                return hashToMemberName.get(tailMap.firstKey());
            }
        }
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

        return hashToMemberName.equals(ring.hashToMemberName);
    }

    @Override
    public int hashCode() {
        return hashToMemberName.hashCode();
    }

    @Override
    public String toString() {
        StringWriter w = new StringWriter();
        w.append("ConsistentHash: {");
        Iterator<Map.Entry<String, String>> iterator = hashToMemberName.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            w.append(entry.getKey() + " -> " + entry.getValue());
            if (iterator.hasNext()) {
                w.append(", ");
            }
        }
        w.append("}");
        return w.toString();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(hashToMemberName.size());
        for (Map.Entry<String, String> entry : hashToMemberName.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue());
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        for (int t = 0; t < size; t++) {
            String key = in.readUTF();
            String value = in.readUTF();
            hashToMemberName.put(key, value);
        }
    }
}
