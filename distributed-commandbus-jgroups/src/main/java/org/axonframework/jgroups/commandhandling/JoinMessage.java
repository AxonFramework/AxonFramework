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

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.jgroups.util.Streamable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This message represents a notification of a Member joining the DistributedCommandBus with a given
 * {@code loadFactor}. Upon receiving this message, members should recalculate their Consistent Hash Ring,
 * including this member.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JoinMessage implements Externalizable {

    private static final long serialVersionUID = 1456658552741424773L;
    private CommandMessageFilter messageFilter;
    private boolean expectReply;
    private int loadFactor;
    private int order;

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
     * @param loadFactor    The loadFactor the member wishes to join with
     * @param messageFilter A predicate the will filter command messages this node will accept.
     * @param order         The index of this update, allowing recipients to order them
     * @param expectReply   Indicates whether the sending member expects a reply with membership information
     */
    public JoinMessage(int loadFactor, CommandMessageFilter messageFilter, int order,
                       boolean expectReply) {
        this.loadFactor = loadFactor;
        this.messageFilter = messageFilter;
        this.order = order;
        this.expectReply = expectReply;
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
     * Indicates whether the sender of this message expects a reply
     *
     * @return whether the sender of this message expects a reply
     */
    public boolean isExpectReply() {
        return expectReply;
    }

    /**
     * The index of this message compared to others about the same sender.
     *
     * @return the relative order of this update
     */
    public int getOrder() {
        return order;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(loadFactor);
        out.writeObject(messageFilter);
        out.writeInt(order);
        out.writeBoolean(expectReply);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        loadFactor = in.readInt();
        messageFilter = (CommandMessageFilter) in.readObject();
        order = in.readInt();
        expectReply = in.readBoolean();
    }

    /**
     * Returns the command message filter used by the member.
     *
     * @return the member's message filter
     */
    public CommandMessageFilter messageFilter() {
        return messageFilter;
    }
}
