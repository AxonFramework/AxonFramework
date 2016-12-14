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

package org.axonframework.jgroups.commandhandling;

import org.axonframework.commandhandling.CommandMessage;
import org.jgroups.Address;
import org.jgroups.util.Streamable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.function.Predicate;

/**
 * This message represents a notification of a Member joining the DistributedCommandBus with a given
 * {@code loadFactor}. Upon receiving this message, members should recalculate their Consistent Hash Ring,
 * including this member.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JoinMessage implements Externalizable {

    private static final long serialVersionUID = 5829153340455127795L;
    private Predicate<CommandMessage<?>> messageFilter;
    private Address address;
    private int loadFactor;

    /**
     * Default constructor required by the {@link Streamable} and {@link Externalizable} interfaces. Do not use
     * directly.
     */
    @SuppressWarnings("UnusedDeclaration")
    public JoinMessage() {

    }

    /**
     * Initializes a JoinMessage with the given {@code loadFactor}.
     *
     * @param address       The address of the cluster member
     * @param loadFactor    The loadFactor the member wishes to join with
     * @param messageFilter A predicate the will filter command messages this node will accept.
     */
    public JoinMessage(Address address, int loadFactor, Predicate<CommandMessage<?>> messageFilter) {
        this.address = address;
        this.loadFactor = loadFactor;
        this.messageFilter = messageFilter;
    }

    /**
     * Returns the loadFactor the member wishes to join with.
     *
     * @return the loadFactor the member wishes to join with.
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(address);
        out.writeInt(loadFactor);
        out.writeObject(messageFilter);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        address = (Address) in.readObject();
        loadFactor = in.readInt();
        messageFilter = (Predicate<CommandMessage<?>>) in.readObject();
    }

    /**
     * Returns the command message filter used by the member.
     *
     * @return the member's message filter
     */
    public Predicate<CommandMessage<?>> messageFilter() {
        return messageFilter;
    }
}
