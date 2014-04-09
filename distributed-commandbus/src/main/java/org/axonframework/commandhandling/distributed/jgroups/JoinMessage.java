/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.distributed.jgroups;

import org.jgroups.util.Streamable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This message represents a notification of a Member joining the DistributedCommandBus with a given
 * <code>loadFactor</code>. Upon receiving this message, members should recalculate their Consistent Hash Ring,
 * including this member.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JoinMessage implements Streamable, Externalizable {

    private static final long serialVersionUID = 5829153340455127795L;
    private int loadFactor;
    private final Set<String> commandNames = new HashSet<String>();

    /**
     * Default constructor required by the {@link Streamable} and {@link Externalizable} interfaces. Do not use
     * directly.
     */
    @SuppressWarnings("UnusedDeclaration")
    public JoinMessage() {
    }

    /**
     * Initializes a JoinMessage with the given <code>loadFactor</code>.
     *
     * @param loadFactor   The loadFactor the member wishes to join with
     * @param commandNames The command types supported by this node as fully qualified class names.
     */
    public JoinMessage(int loadFactor, Set<String> commandNames) {
        this.loadFactor = loadFactor;
        this.commandNames.addAll(commandNames);
    }

    /**
     * Returns the loadFactor the member wishes to join with.
     *
     * @return the loadFactor the member wishes to join with.
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    /**
     * Returns a read-only view on the Command Names supported by the joining member. Each String in the given Set
     * represents the name of a supported command.
     *
     * @return a read-only view on the Command Types supported by the joining member
     */
    public Set<String> getCommandNames() {
        return Collections.unmodifiableSet(commandNames);
    }

    @Override
    public void writeTo(DataOutput out) throws IOException {
        out.writeInt(loadFactor);
        out.writeInt(commandNames.size());
        for (String type : commandNames) {
            out.writeUTF(type);
        }
    }

    @Override
    public void readFrom(DataInput in) throws IOException {
        loadFactor = in.readInt();
        int typeCount = in.readInt();
        commandNames.clear();
        for (int t = 0; t < typeCount; t++) {
            commandNames.add(in.readUTF());
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        writeTo(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        readFrom(in);
    }
}
