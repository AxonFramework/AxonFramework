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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.serialization.Serializer;
import org.jgroups.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * A Connector for the {@link DistributedCommandBus} based on JGroups that acts both as the discovery and routing
 * mechanism (implementing {@link CommandRouter}) as well as the Connector between nodes
 * (implementing {@link CommandBusConnector}).
 * <p>
 * After configuring the Connector, it needs to {@link #connect()}, before it can start dispatching messages to other
 * nodes. For a clean shutdown, connectors should {@link #disconnect()} to notify other nodes of the node leaving.
 */
public class JGroupsConnector implements CommandRouter, Receiver, CommandBusConnector {
    private static final Logger logger = LoggerFactory.getLogger(JGroupsConnector.class);

    private final CommandBus localSegment;
    private final CommandCallbackRepository<Address> callbackRepository = new CommandCallbackRepository<>();
    private final Serializer serializer;
    private final JoinCondition joinedCondition = new JoinCondition();
    private final Map<Address, SimpleMember<Address>> members = new HashMap<>();
    private final String clusterName;
    private final RoutingStrategy routingStrategy;
    private final JChannel channel;
    private final AtomicReference<ConsistentHash> consistentHash = new AtomicReference<>(new ConsistentHash());
    private volatile View currentView;
    private volatile int loadFactor = 0;
    private volatile Predicate<CommandMessage<?>> commandFilter = DenyAll.INSTANCE;

    /**
     * Initialize the connector using the given {@code localSegment} to handle commands on the local node, and the given
     * {@code channel} to connect between nodes. A unique {@code clusterName} should be chose to define which nodes can
     * connect to each other. The given {@code serializer} is used to serialize messages when they are sent between
     * nodes.
     * <p>
     * Commands are routed based on the {@link org.axonframework.commandhandling.TargetAggregateIdentifier}
     *
     * @param localSegment The CommandBus implementation that handles the local Commands
     * @param channel      The JGroups Channel used to communicate between nodes
     * @param clusterName  The name of the Cluster
     * @param serializer   The serializer to serialize Command Messages with
     */
    public JGroupsConnector(CommandBus localSegment, JChannel channel, String clusterName, Serializer serializer) {
        this(localSegment, channel, clusterName, serializer, new AnnotationRoutingStrategy());
    }

    /**
     * Initialize the connector using the given {@code localSegment} to handle commands on the local node, and the given
     * {@code channel} to connect between nodes. A unique {@code clusterName} should be chose to define which nodes can
     * connect to each other. The given {@code serializer} is used to serialize messages when they are sent between
     * nodes. The {@code routingStrategy} is used to define the key based on which Command Messages are routed to their
     * respective handler nodes.
     *
     * @param localSegment    The CommandBus implementation that handles the local Commands
     * @param channel         The JGroups Channel used to communicate between nodes
     * @param clusterName     The name of the Cluster
     * @param serializer      The serializer to serialize Command Messages with
     * @param routingStrategy The strategy for routing Commands to a Node
     */
    public JGroupsConnector(CommandBus localSegment, JChannel channel, String clusterName, Serializer serializer,
                            RoutingStrategy routingStrategy) {
        this.localSegment = localSegment;
        this.serializer = serializer;
        this.channel = channel;
        this.clusterName = clusterName;
        this.routingStrategy = routingStrategy;
    }

    @Override
    public void updateMembership(int loadFactor, Predicate<CommandMessage<?>> commandFilter) {
        this.loadFactor = loadFactor;
        this.commandFilter = commandFilter;
        broadCastMembership();
    }

    /**
     * Send the local membership details (load factor and supported Command types) to other member nodes of this
     * cluster.
     *
     * @throws ServiceRegistryException when an exception occurs sending membership details to other nodes
     */
    protected void broadCastMembership() throws ServiceRegistryException {
        try {
            if (channel.isConnected()) {
                Address localAddress = channel.getAddress();
                Message joinMessage = new Message(null, new JoinMessage(localAddress, loadFactor, commandFilter));
                joinMessage.setFlag(Message.Flag.OOB);
                channel.send(joinMessage);
            }
        } catch (Exception e) {
            throw new ServiceRegistryException("Could not broadcast local membership details to the cluster", e);
        }
    }

    /**
     * Connects this Node to the cluster and shares membership details about this node with the other nodes in the
     * cluster.
     * <p>
     * The Join messages have been sent, but may not have been processed yet when the method returns. Before sending
     * messages via this connector, await for the joining process to be completed (see {@link #awaitJoined() and
     * {@link #awaitJoined(long, TimeUnit)}}.
     *
     * @throws Exception when an error occurs connecting or communicating with the cluster
     */
    public void connect() throws Exception {
        if (channel.getClusterName() != null && !clusterName.equals(channel.getClusterName())) {
            throw new ConnectionFailedException("Already joined cluster: " + channel.getClusterName());
        }
        channel.setReceiver(this);
        channel.connect(clusterName);
        broadCastMembership();

        Address localAddress = channel.getAddress();
        String localName = channel.getName(localAddress);
        SimpleMember<Address> localMember = new SimpleMember<>(localName, localAddress, null);
        members.put(localAddress, localMember);
        consistentHash.updateAndGet(ch -> ch.with(localMember, loadFactor, commandFilter));
    }

    /**
     * Disconnects from the Cluster, preventing any Commands from being routed to this node.
     */
    public void disconnect() {
        channel.disconnect();
    }

    @Override
    public void getState(OutputStream ostream) throws Exception {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setState(InputStream istream) throws Exception {
    }

    @Override
    public void viewAccepted(final View view) {
        if (currentView == null) {
            logger.info("Local segment ({}) joined the cluster. Broadcasting configuration.", channel.getAddress());
            try {
                broadCastMembership();
                joinedCondition.markJoined(true);
            } catch (Exception e) {
                throw new MembershipUpdateFailedException("Failed to broadcast my settings", e);
            }
        } else if (!view.equals(currentView)) {
            Address[][] diff = View.diff(currentView, view);
            Address[] joined = diff[0];
            Address[] left = diff[1];

            stream(joined).filter(member -> !member.equals(channel.getAddress())).forEach(member -> {
                logger.info("New member detected: [{}]. Sending it my configuration.", member);
                try {
                    Message joinMessage = new Message(member, new JoinMessage(channel.getAddress(), loadFactor, commandFilter));
                    joinMessage.setFlag(Message.Flag.OOB);
                    channel.send(joinMessage);
                } catch (Exception e) {
                    throw new MembershipUpdateFailedException("Failed to notify my existence to " + member);
                }
            });

            stream(left).forEach(lm -> consistentHash.updateAndGet(ch -> {
                SimpleMember<Address> member = members.get(lm);
                if (member == null) {
                    return ch;
                }
                return ch.without(member);
            }));
            stream(left).forEach(members::remove);
        }
        currentView = view;
    }

    @Override
    public void suspect(Address suspected_mbr) {
        logger.warn("Member is suspect: {}", suspected_mbr.toString());
    }

    @Override
    public void block() {
        //We are not going to block
    }

    @Override
    public void unblock() {
        //We are not going to block
    }

    @Override
    public void receive(Message msg) {
        Object message = msg.getObject();
        if (message instanceof JoinMessage) {
            processJoinMessage(msg, (JoinMessage) message);
        } else if (message instanceof JGroupsDispatchMessage) {
            processDispatchMessage(msg, (JGroupsDispatchMessage) message);
        } else if (message instanceof JGroupsReplyMessage) {
            processReplyMessage((JGroupsReplyMessage) message);
        }
    }

    private void processReplyMessage(JGroupsReplyMessage message) {
        CommandCallbackWrapper<Object, Object, Object> callbackWrapper =
                callbackRepository.fetchAndRemove(message.getCommandIdentifier());
        if (callbackWrapper == null) {
            logger.warn(
                    "Received a callback for a message that has either already received a callback, or which was not " +
                            "sent through this node. Ignoring.");
        } else {
            if (message.isSuccess()) {
                callbackWrapper.success(message.getReturnValue(serializer));
            } else {
                Throwable exception = getOrDefault(message.getError(serializer), new IllegalStateException(
                        format("Unknown execution failure for command [%s]", message.getCommandIdentifier())));
                callbackWrapper.fail(exception);
            }
        }
    }

    private <C, R> void processDispatchMessage(Message msg, JGroupsDispatchMessage message) {
        if (message.isExpectReply()) {
            try {
                CommandMessage commandMessage = message.getCommandMessage(serializer);
                //noinspection unchecked
                localSegment.dispatch(commandMessage, new CommandCallback<C, R>() {
                    @Override
                    public void onSuccess(CommandMessage<? extends C> commandMessage, R result) {
                        sendReply(msg.getSrc(), message.getCommandIdentifier(), result, null);
                    }

                    @Override
                    public void onFailure(CommandMessage<? extends C> commandMessage, Throwable cause) {
                        sendReply(msg.getSrc(), message.getCommandIdentifier(), null, cause);
                    }
                });
            } catch (Exception e) {
                sendReply(msg.getSrc(), message.getCommandIdentifier(), null, e);
            }
        } else {
            try {
                localSegment.dispatch(message.getCommandMessage(serializer));
            } catch (Exception e) {
                logger.error("Could not dispatch command", e);
            }
        }
    }

    private <R> void sendReply(Address address, String commandIdentifier, R result, Throwable cause) {
        boolean success = cause == null;
        Object reply;
        try {
            reply = new JGroupsReplyMessage(commandIdentifier, success, success ? result : cause, serializer);
        } catch (Exception e) {
            logger.warn(String.format("Could not serialize command reply [%s]. Sending back NULL.",
                                      success ? result : cause), e);
            reply = new JGroupsReplyMessage(commandIdentifier, success, null, serializer);
        }
        try {
            channel.send(address, reply);
        } catch (Exception e) {
            logger.error("Could not send reply", e);
        }
    }

    private void processJoinMessage(final Message message, final JoinMessage joinMessage) {
        String joinedMember = channel.getName(message.getSrc());
        if (joinedMember != null) {
            int loadFactor = joinMessage.getLoadFactor();
            Predicate<CommandMessage<?>> commandFilter = joinMessage.messageFilter();
            SimpleMember<Address> member = new SimpleMember<>(joinedMember, message.getSrc(), null);
            members.put(member.endpoint(), member);
            consistentHash.updateAndGet(ch -> ch.with(member, loadFactor, commandFilter));
            if (logger.isInfoEnabled() && !message.getSrc().equals(channel.getAddress())) {
                logger.info("{} joined with load factor: {}", joinedMember, loadFactor);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Got a network of members: {}", members.values());
            }
        } else {
            logger.warn("Received join message from '{}', but a connection with the sender has been lost.",
                        message.getSrc().toString());
        }
    }

    /**
     * this method blocks until this member has successfully joined the other members, until the thread is
     * interrupted, or when joining has failed.
     *
     * @return {@code true} if the member successfully joined, otherwise {@code false}.
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
     * @return {@code true} if the member successfully joined, otherwise {@code false}.
     * @throws InterruptedException when the thread is interrupted while joining
     */
    public boolean awaitJoined(long timeout, TimeUnit timeUnit) throws InterruptedException {
        joinedCondition.await(timeout, timeUnit);
        return joinedCondition.isJoined();
    }

    /**
     * Returns the name of the current node, as it is known to the Cluster.
     *
     * @return the name of the current node
     */
    public String getNodeName() {
        return channel.getName();
    }

    /**
     * Returns the ConsistentHash instance that describes the current membership status. The {@link ConsistentHash} is
     * used to decide which node is to be sent a Message.
     *
     * @return the ConsistentHash instance that describes the current membership status
     */
    protected ConsistentHash getConsistentHash() {
        return consistentHash.get();
    }

    @Override
    public <C> void send(Member destination, CommandMessage<? extends C> command) throws Exception {
        channel.send(resolveAddress(destination), new JGroupsDispatchMessage(command, serializer, false));
    }

    @Override
    public <C, R> void send(Member destination, CommandMessage<C> command,
                            CommandCallback<? super C, R> callback) throws Exception {
        callbackRepository.store(command.getIdentifier(), new CommandCallbackWrapper<>(destination, command, callback));
        channel.send(resolveAddress(destination), new JGroupsDispatchMessage(command, serializer, true));
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return localSegment.subscribe(commandName, handler);
    }

    /**
     * Resolve the JGroups Address of the given {@code Member}.
     *
     * @param destination The node of which to solve the Address
     * @return The JGroups Address of the given node
     * @throws CommandBusConnectorCommunicationException when an error occurs resolving the adress
     */
    protected Address resolveAddress(Member destination) {
        return destination.getConnectionEndpoint(Address.class).orElseThrow(
                () -> new CommandBusConnectorCommunicationException(
                        "The target member doesn't expose a JGroups endpoint"));
    }

    @Override
    public Optional<Member> findDestination(CommandMessage<?> message) {
        String routingKey = routingStrategy.getRoutingKey(message);
        return consistentHash.get().getMember(routingKey, message);
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
}
