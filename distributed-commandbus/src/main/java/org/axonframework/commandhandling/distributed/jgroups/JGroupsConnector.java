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

package org.axonframework.commandhandling.distributed.jgroups;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static java.util.Arrays.asList;

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
    private volatile View currentView;
    private volatile int loadFactor = 0;
    private volatile Predicate<CommandMessage<?>> commandFilter = DenyAll.INSTANCE;

    private final AtomicReference<ConsistentHash> consistentHash = new AtomicReference<>(new ConsistentHash());

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

    protected void broadCastMembership() throws ServiceRegistryException {
        try {
            if (channel.isConnected()) {
                Address localAddress = channel.getAddress();
                channel.send(null, new JoinMessage(localAddress, loadFactor, commandFilter));
            }
        } catch (Exception e) {
            throw new ServiceRegistryException("Could not broadcast local membership details to the cluster", e);
        }
    }

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
            Address[] joined = View.diff(currentView, view)[0];
            Address[] left = View.diff(currentView, view)[1];

            asList(joined).stream()
                    .filter(member -> !member.equals(channel.getAddress()))
                    .forEach(member -> {
                        logger.info("New member detected: [{}]. Sending it my configuration.", member);
                        try {
                            channel.send(member, new JoinMessage(channel.getAddress(), loadFactor, commandFilter));
                        } catch (Exception e) {
                            throw new MembershipUpdateFailedException("Failed to notify my existence to " + member);
                        }
                    });

            Arrays.stream(left).forEach(lm -> consistentHash.updateAndGet(ch -> ch.without(members.get(lm))));
            Arrays.stream(left).forEach(members::remove);
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
        } else if (message instanceof DispatchMessage) {
            processDispatchMessage(msg, (DispatchMessage) message);
        } else if (message instanceof ReplyMessage) {
            processReplyMessage((ReplyMessage) message);
        }
    }

    private void processReplyMessage(ReplyMessage message) {
        CommandCallbackWrapper<Object, Object, Object> callbackWrapper =
                callbackRepository.fetchAndRemove(message.getCommandIdentifier());
        if (callbackWrapper == null) {
            logger.warn("Received a callback for a message that has either already received a callback, or which was not sent through this node. Ignoring.");
        } else {
            if (message.isSuccess()) {
                callbackWrapper.success(message.getReturnValue(serializer));
            } else {
                callbackWrapper.fail(message.getError(serializer));
            }
        }
    }

    private <C, R> void processDispatchMessage(Message msg, DispatchMessage message) {
        if (message.isExpectReply()) {
            try {
                CommandMessage commandMessage = message.getCommandMessage(serializer);
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
        try {
            channel.send(address, new ReplyMessage(commandIdentifier, result, cause, serializer));
        } catch (Exception e) {
            try {
                channel.send(address, new ReplyMessage(commandIdentifier, null, e, serializer));
            } catch (Exception e1) {
                logger.error("Could not send reply", e1);
            }
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
     * @return <code>true</code> if the member successfully joined, otherwise <code>false</code>.
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
     * @throws InterruptedException when the thread is interrupted while joining
     */
    public boolean awaitJoined(long timeout, TimeUnit timeUnit) throws InterruptedException {
        joinedCondition.await(timeout, timeUnit);
        return joinedCondition.isJoined();
    }

    public String getNodeName() {
        return channel.getName();
    }

    protected ConsistentHash getConsistentHash() {
        return consistentHash.get();
    }

    @Override
    public <C> void send(Member destination, CommandMessage<? extends C> command) throws Exception {
        channel.send(resolveAddress(destination), new DispatchMessage(command, serializer, false));
    }

    @Override
    public <C, R> void send(Member destination, CommandMessage<C> command, CommandCallback<? super C, R> callback) throws Exception {
        callbackRepository.store(command.getIdentifier(), new CommandCallbackWrapper<>(destination, command, callback));
        channel.send(resolveAddress(destination), new DispatchMessage(command, serializer, true));
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return localSegment.subscribe(commandName, handler);
    }

    protected Address resolveAddress(Member destination) {
        return destination.getConnectionEndpoint(Address.class).orElseThrow(() -> new CommandBusConnectorCommunicationException("The target member doesn't expose a JGroups endpoint"));
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
