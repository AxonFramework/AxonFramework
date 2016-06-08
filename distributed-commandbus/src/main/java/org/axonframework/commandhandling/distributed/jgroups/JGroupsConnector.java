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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.commandhandling.distributed.ConsistentHash;
import org.axonframework.commandhandling.distributed.RemoteCommandHandlingException;
import org.axonframework.commandhandling.distributed.jgroups.support.callbacks.ReplyingCallback;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.Serializer;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A CommandBusConnector that uses JGroups to discover and connect to other JGroupsConnectors in the network. Depending
 * on the configuration of the {@link JChannel channel} that was provided, this implementation allows for a dynamic
 * discovery and addition of new members. When members disconnect, their portion of the processing is divided over the
 * remaining members.
 * <p/>
 * This connector uses a consistent hashing algorithm to route commands. This ensures that commands with the same
 * routing key will be sent to the same member, regardless of the sending member of that message.
 * <p/>
 * Members join the CommandBus using a load factor (see {@link #connect(int)}). This load factor defines the number
 * of sections on the consistent hash ring a node will receive. The more nodes on the ring, the bigger the relative
 * load a member receives. Using a higher number of hashes will also result in a more evenly distribution of load over
 * the different members.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JGroupsConnector implements CommandBusConnector {

    private static final Logger logger = LoggerFactory.getLogger(JGroupsConnector.class);

    private final JChannel channel;
    private AtomicReference<ConsistentHash> consistentHash =
            new AtomicReference<>(ConsistentHash.emptyRing());
    private final String clusterName;
    private final CommandBus localSegment;
    private final MessageSerializer serializer;
    private final JoinCondition joinedCondition = new JoinCondition();
    private final ConcurrentMap<String, CallbackHolder> callbacks =
            new ConcurrentHashMap<>();
    private final Set<String> supportedCommandNames = new CopyOnWriteArraySet<>();
    private volatile int currentLoadFactor;
    private final JGroupsConnector.MessageReceiver messageReceiver;
    private final HashChangeListener hashChangeListener;

    /**
     * Initializes the Connector using given resources. The <code>channel</code> is used to connect this connector to
     * the other members. The <code>clusterName</code> is the name of the cluster the channel will be connected to. For
     * local dispatching of commands, the given <code>localSegment</code> is used. When messages are remotely
     * dispatched, the given <code>serializer</code> is used to serialize and deserialize the messages.
     * <p/>
     * Note that Connectors on different members need to have the same <code>channel</code> configuration,
     * <code>clusterName</code> and <code>serializer</code> configuration in order to successfully set up a distributed
     * cluster.
     *
     * @param channel      The channel (configured, but not connected) used to discover and connect with the other
     *                     members
     * @param clusterName  The name of the cluster to connect to
     * @param localSegment The command bus on which messages with this member as destination are dispatched on
     * @param serializer   The serialized used to serialize messages before sending them to other members.
     */
    public JGroupsConnector(JChannel channel, String clusterName, CommandBus localSegment, Serializer serializer) {
        this(channel, clusterName, localSegment, serializer, null);
    }

    /**
     * Initializes the Connector using given resources. The <code>channel</code> is used to connect this connector to
     * the other members. The <code>clusterName</code> is the name of the cluster the channel will be connected to. For
     * local dispatching of commands, the given <code>localSegment</code> is used. When messages are remotely
     * dispatched, the given <code>serializer</code> is used to serialize and deserialize the messages.
     * <p/>
     * Note that Connectors on different members need to have the same <code>channel</code> configuration,
     * <code>clusterName</code> and <code>serializer</code> configuration in order to successfully set up a distributed
     * cluster.
     *
     * @param channel            The channel (configured, but not connected) used to discover and connect with the
     *                           other members
     * @param clusterName        The name of the cluster to connect to
     * @param localSegment       The command bus on which messages with this member as destination are dispatched on
     * @param serializer         The serialized used to serialize messages before sending them to other members.
     * @param hashChangeListener The listener to notify when the consistent hash is changed
     */
    public JGroupsConnector(JChannel channel, String clusterName, CommandBus localSegment, Serializer serializer,
                            HashChangeListener hashChangeListener) {
        this.channel = channel;
        this.clusterName = clusterName;
        this.localSegment = localSegment;
        this.hashChangeListener = hashChangeListener;
        this.serializer = new MessageSerializer(serializer);
        this.messageReceiver = new MessageReceiver();
    }

    /**
     * Connects this member to the cluster using the given <code>loadFactor</code>. The <code>loadFactor</code> defines
     * the (approximate) relative load that this member will receive.
     * <p/>
     * A good default value is 100, which will give this member 100 nodes on the distributed hash ring. Giving all
     * members (proportionally) lower values will result in a less evenly distributed hash.
     *
     * @param loadFactor The load factor for this node.
     * @throws ConnectionFailedException when an error occurs while connecting
     */
    public synchronized void connect(int loadFactor) throws ConnectionFailedException {
        this.currentLoadFactor = loadFactor;
        Assert.isTrue(loadFactor >= 0, "Load Factor must be a positive integer value.");
        Assert.isTrue(channel.getReceiver() == null || channel.getReceiver() == messageReceiver,
                      "The given channel already has a receiver configured. "
                              + "Has the channel been reused with other Connectors?");
        try {
            channel.setReceiver(messageReceiver);
            if (channel.isConnected() && !clusterName.equals(channel.getClusterName())) {
                throw new AxonConfigurationException("The Channel that has been configured with this JGroupsConnector "
                                                             + "is already connected, but not through this cluster");
            } else if (channel.isConnected()) {
                // we need to synchronize state now that we have attached our MessageReceiver
                logger.info("Reading configuration from cluster.");
                channel.getState(null, 10000);
                // make sure the current view is processed
                messageReceiver.viewAccepted(channel.getView());
            } else {
                logger.info("Connecting to the cluster.");
                channel.connect(clusterName);
            }
        } catch (Exception e) {
            joinedCondition.markJoined(false);
            channel.disconnect();
            throw new ConnectionFailedException("Failed to connect to JGroupsConnectorFactoryBean", e);
        }
    }

    private void sendMembershipUpdate(Address dest) throws MembershipUpdateFailedException {
        try {
            if (channel.isConnected()) {
                final HashSet<String> commandNames = new HashSet<>(supportedCommandNames);
                updateConsistentHash(hash -> hash.withAdditionalNode(getNodeName(), currentLoadFactor, commandNames));
                channel.send(new Message(dest, new JoinMessage(currentLoadFactor,
                                                               new HashSet<>(supportedCommandNames)))
                                     .setFlag(Message.Flag.RSVP));
            }
        } catch (Exception e) {
            throw new MembershipUpdateFailedException(
                    "Failed to dispatch Join message to Distributed Command Bus Members", e);
        }
    }

    /**
     * this method blocks until this member has successfully joined the other members, until the thread is
     * interrupted, or when joining has failed.
     *
     * @return <code>true</code> if the member successfully joined, otherwise <code>false</code>.
     *
     * @throws InterruptedException when the thread is interrupted while joining
     */
    public boolean awaitJoined() throws InterruptedException {
        joinedCondition.await();
        return joinedCondition.isJoined();
    }

    /**
     * this method blocks until this member has successfully joined the other members, until the thread is
     * interrupted, when the given number of milliseconds have passed, or when joining has failed.
     *
     * @param timeout  The amount of time to wait for the connection to complete
     * @param timeUnit The time unit of the timeout
     * @return <code>true</code> if the member successfully joined, otherwise <code>false</code>.
     *
     * @throws InterruptedException when the thread is interrupted while joining
     */
    public boolean awaitJoined(long timeout, TimeUnit timeUnit) throws InterruptedException {
        joinedCondition.await(timeout, timeUnit);
        return joinedCondition.isJoined();
    }

    @Override
    public <C, R> void send(String routingKey, CommandMessage<C> commandMessage, CommandCallback<? super C, R> callback)
            throws Exception {
        Address dest = resolveDestination(routingKey, commandMessage);
        callbacks.put(commandMessage.getIdentifier(), new CallbackHolder<>(dest, commandMessage, callback));
        channel.send(dest, new DispatchMessage(commandMessage, serializer, true));
    }

    @Override
    public <C> void send(String routingKey, CommandMessage<C> commandMessage) throws Exception {
        Address dest = resolveDestination(routingKey, commandMessage);
        channel.send(dest, new DispatchMessage(commandMessage, serializer, false));
    }

    private Address resolveDestination(String routingKey, CommandMessage<?> commandMessage)
            throws InterruptedException {
        Assert.isTrue(awaitJoined(5, TimeUnit.SECONDS), "This Connector did not properly join the Cluster yet.");
        String destination = consistentHash.get().getMember(routingKey, commandMessage.getCommandName());
        if (destination == null) {
            throw new CommandDispatchException("No node known to accept " + commandMessage.getCommandName());
        }
        return getAddress(destination);
    }

    @Override
    public synchronized Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        Registration subscription = localSegment.subscribe(commandName, handler);
        if (supportedCommandNames.add(commandName)) {
            sendMembershipUpdate(null);
        }
        return () -> {
            synchronized (this) {
                if (subscription.cancel()) {
                    if (supportedCommandNames.remove(commandName)) {
                        sendMembershipUpdate(null);
                    }
                    return true;
                }
                return false;
            }
        };
    }

    private Address getAddress(String nodeName) {
        for (Address member : channel.getView()) {
            if (channel.getName(member).equals(nodeName)) {
                return member;
            }
        }
        throw new IllegalArgumentException("Given node doesn't seem to be a member of the DistributedCommandBus");
    }

    private ConsistentHashChange updateConsistentHash(UpdateFunction function) {
        ConsistentHash current = consistentHash.get();
        ConsistentHash newHash = function.update(current);
        while (!consistentHash.compareAndSet(current, newHash)) {
            current = consistentHash.get();
            newHash = function.update(current);
        }
        if (!newHash.equals(current) && hashChangeListener != null) {
            hashChangeListener.hashChanged(newHash);
        }
        return new ConsistentHashChange(current, newHash);
    }

    /**
     * Returns the consistent hash on which current assignment of commands to nodes is being executed.
     *
     * @return the consistent hash on which current assignment of commands to nodes is being executed
     */
    public ConsistentHash getConsistentHash() {
        return consistentHash.get();
    }

    /**
     * Returns the set of members currently registered with the connector.
     * <p/>
     * Note that any changes in membership are not reflected in the returned set.
     *
     * @return the set of members currently registered with the connector
     */
    public Set<ConsistentHash.Member> getMembers() {
        return consistentHash.get().getMembers();
    }

    /**
     * Returns the name of this node in the cluster, or null if this member is not connected.
     *
     * @return the name of this node in the cluster
     */
    public String getNodeName() {
        return channel.getName();
    }

    private class MessageReceiver extends ReceiverAdapter {

        private volatile View currentView;

        @Override
        public void getState(OutputStream ostream) throws Exception {
            Util.objectToStream(consistentHash.get(), new DataOutputStream(ostream));
        }

        @Override
        public void setState(InputStream istream) throws Exception {
            consistentHash.set((ConsistentHash) Util.objectFromStream(new DataInputStream(istream)));
        }

        @Override
        public void viewAccepted(final View view) {
            ConsistentHashChange hashChange = updateConsistentHash(hash -> hash.withExclusively(getMemberNames(view)));
            if (hashChange.isChange()) {
                int messagesLost = 0;
                // check whether the members with outstanding callbacks are all alive
                for (Map.Entry<String, CallbackHolder> entry : callbacks.entrySet()) {
                    if (!entry.getValue().isMemberLive(view)) {
                        CallbackHolder callback = callbacks.remove(entry.getKey());
                        if (callback != null) {
                            messagesLost++;
                            callback.reportFailure(new RemoteCommandHandlingException(
                                    "The connection with the destination was lost before the result was reported."));
                        }
                    }
                }
                reportDisconnectedMembers(hashChange);
                logger.debug("New distributed hash: {}", hashChange.newHash.toString());
                if (messagesLost > 0 && logger.isWarnEnabled()) {
                    logger.warn(
                            "A member was disconnected while waiting for a reply. {} messages are lost without reply.",
                            messagesLost);
                }
            }
            if (currentView == null) {
                logger.info("Local segment ({}) joined the cluster. Broadcasting configuration.", channel.getAddress());
                sendMembershipUpdate(null);
                joinedCondition.markJoined(true);
            } else if (!view.equals(currentView)) {
                view.getMembers().stream()
                    .filter(member -> (currentView == null || !currentView.containsMember(member))
                            && !member.equals(channel.getAddress()))
                    .forEach(member -> {
                        logger.info("New member detected: [{}]. Sending it my configuration.", member);
                        sendMembershipUpdate(member);
                    });
            }
            currentView = view;
        }

        private void reportDisconnectedMembers(ConsistentHashChange hashChange) {
            Set<ConsistentHash.Member> newMembers = hashChange.newHash.getMembers();
            Set<ConsistentHash.Member> oldMembers = new HashSet<>(hashChange.oldHash.getMembers());
            oldMembers.removeAll(newMembers);
            String[] memberNames = new String[oldMembers.size()];
            int i = 0;
            for (ConsistentHash.Member member : oldMembers) {
                memberNames[i++] = member.name();
            }

            logger.info("Member(s) disconnected: {}. Rebuilt consistent hash ring.", Arrays.toString(memberNames));
        }

        @Override
        public void suspect(Address mbr) {
            if (logger.isWarnEnabled()) {
                logger.warn("Suspect member: {}.", channel.getName(mbr));
            }
        }

        @Override
        public void receive(Message msg) {
            Object message = msg.getObject();
            if (message instanceof JoinMessage) {
                processJoinMessage(msg, (JoinMessage) message);
            } else if (message instanceof DispatchMessage) {
                processDispatchMessage(msg, (DispatchMessage) message);
            } else if (message instanceof ReplyMessage) {
                processReplyMessage((ReplyMessage) message);
            }
        }

        private void processDispatchMessage(final Message msg, final DispatchMessage message) {
            try {
                final CommandMessage commandMessage = message.getCommandMessage(serializer);
                if (message.isExpectReply()) {
                    localSegment.dispatch(commandMessage, new ReplyingCallback(channel,
                                                                               msg.getSrc(), serializer
                    ));
                } else {
                    localSegment.dispatch(commandMessage);
                }
            } catch (Exception e) {
                if (message.isExpectReply()) {
                    final String commandIdentifier = message.getCommandIdentifier();
                    try {
                        channel.send(msg.getSrc(), new ReplyMessage(commandIdentifier, null, e, serializer));
                    } catch (Exception errorInReply) {
                        logger.error("Unable to notify sender of failure to read message with id '{}'."
                                             + "description of reading failure ", commandIdentifier, e);
                        logger.error("Failed to notify sender of failed message with id '{}', reason: ",
                                     commandIdentifier, errorInReply);
                    }
                } else {
                    throw e;
                }
            }
        }

        private void processJoinMessage(final Message msg, final JoinMessage joinMessage) {
            final String channelName = channel.getName(msg.getSrc());
            if (channelName != null) {
                updateConsistentHash(hash -> hash.withAdditionalNode(channelName, joinMessage.getLoadFactor(),
                                                                     joinMessage.getCommandNames()));
                if (logger.isInfoEnabled() && !msg.getSrc().equals(channel.getAddress())) {
                    logger.info("{} joined with load factor: {}", msg.getSrc(), joinMessage.getLoadFactor());
                }
            } else {
                logger.warn("Received join message from '{}', but a connection with the sender has been lost.",
                            msg.getSrc().toString());
            }
        }

        @SuppressWarnings("unchecked")
        private void processReplyMessage(ReplyMessage replyMessage) {
            CallbackHolder callback = callbacks.remove(replyMessage.getCommandIdentifier());
            if (callback != null) {
                if (replyMessage.isSuccess()) {
                    callback.reportSuccess(replyMessage.getReturnValue(serializer));
                } else {
                    callback.reportFailure(replyMessage.getError(serializer));
                }
            }
        }
    }

    private List<String> getMemberNames(View view) {
        return view.getMembers().stream().map(channel::getName).collect(Collectors.toList());
    }

    private static final class JoinCondition {

        private final CountDownLatch joinCountDown = new CountDownLatch(1);
        private volatile boolean success;

        public void await() throws InterruptedException {
            joinCountDown.await();
        }

        public void await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            joinCountDown.await(timeout, timeUnit);
        }

        private void markJoined(boolean joinSucceeded) {
            this.success = joinSucceeded;
            joinCountDown.countDown();
        }

        public boolean isJoined() {
            return success;
        }
    }

    private static class CallbackHolder<C, R> {

        private final Address dest;
        private final CommandCallback<? super C, R> callback;
        private final CommandMessage<C> commandMessage;

        public CallbackHolder(Address dest, CommandMessage<C> commandMessage,
                              CommandCallback<? super C, R> callback) {
            this.dest = dest;
            this.callback = callback;
            this.commandMessage = commandMessage;
        }

        public boolean isMemberLive(View currentView) {
            return currentView.containsMember(dest);
        }

        public void reportSuccess(R result) {
            callback.onSuccess(commandMessage, result);
        }

        public void reportFailure(Throwable cause) {
            callback.onFailure(commandMessage, cause);
        }
    }

    @FunctionalInterface
    private interface UpdateFunction {

        ConsistentHash update(ConsistentHash hash);
    }

    private static class ConsistentHashChange {

        private final ConsistentHash oldHash;
        private final ConsistentHash newHash;

        public ConsistentHashChange(ConsistentHash oldHash, ConsistentHash newHash) {
            this.oldHash = oldHash;
            this.newHash = newHash;
        }

        public boolean isChange() {
            return !newHash.equals(oldHash);
        }
    }
}
