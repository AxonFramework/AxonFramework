package org.axonframework.commandhandling.distributed.jgroups;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.*;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.commandhandling.distributed.registry.AbstractServiceRegistry;
import org.axonframework.commandhandling.distributed.registry.ServiceMember;
import org.axonframework.commandhandling.distributed.registry.ServiceRegistry;
import org.axonframework.commandhandling.distributed.registry.ServiceRegistryException;
import org.axonframework.serializer.Serializer;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.util.Arrays.asList;

public class JGroupsConnector extends AbstractServiceRegistry<Address> implements Receiver, CommandBusConnector<Address> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JGroupsConnector.class);

    private final CommandBus localSegment;
    private final CommandCallbackRepository<Address> callbackRepository = new CommandCallbackRepository<>();
    private final Serializer serializer;
    private Set<Address> currentMembers = new HashSet<>();
    private final JoinCondition joinedCondition = new JoinCondition();
    private final Map<Address, ServiceMember<Address>> members = new HashMap<>();
    private final String clusterName;
    private JChannel channel;
//    private ServiceMember<Address> me;
    private volatile View currentView;
    private int loadFactor = 100;
    private Predicate<CommandMessage> commandFilter = DenyAll.INSTANCE;

    public JGroupsConnector(CommandBus localSegment, JChannel channel, String clusterName, Serializer serializer) {
        this.localSegment = localSegment;
        this.serializer = serializer;
        this.channel = channel;
        this.clusterName = clusterName;
    }

    @Override
    public void publish(Address address, int loadFactor, Predicate<CommandMessage> commandFilter) throws ServiceRegistryException {
        try {
            this.loadFactor = loadFactor;
            this.commandFilter = commandFilter;
            if (channel.isConnected()) {
                JoinMessage joinMessage = new JoinMessage(address, loadFactor, commandFilter);
                channel.send(null, joinMessage);
            }
        } catch (Exception e) {
            throw new ServiceRegistryException("Could not publish me on the cluster", e);
        }
    }

    public void connect(int loadFactor) throws Exception {
        if (channel.getClusterName() != null && !clusterName.equals(channel.getClusterName())) {
            throw new ConnectionFailedException("Already joined cluster: " + channel.getClusterName());
        }
        this.loadFactor = loadFactor;
        channel.setReceiver(this);
        channel.connect(clusterName);
        publish(channel.getAddress(), loadFactor, commandFilter);
    }

    @Override
    public void unpublish(Address address) throws ServiceRegistryException {
        try {
            channel.send(null, new JoinMessage(address, 0, null));
        } catch (Exception e) {
            throw new ServiceRegistryException("Could not unpublish me on the cluster", e);
        }
    }

    @Override
    public void getState(OutputStream ostream) throws Exception {
        Util.objectToStream(members, new DataOutputStream(ostream));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setState(InputStream istream) throws Exception {
        members.clear();
        members.putAll((Map) Util.objectFromStream(new DataInputStream(istream)));
        update(new HashSet<>(members.values()));
    }

    @Override
    public void viewAccepted(final View view) {
        if (currentView == null) {
            LOGGER.info("Local segment ({}) joined the cluster. Broadcasting configuration.", channel.getAddress());
            try {
                channel.send(null, new JoinMessage(channel.getAddress(), loadFactor, commandFilter));
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
                        LOGGER.info("New member detected: [{}]. Sending it my configuration.", member);
                        try {
                            channel.send(member, new JoinMessage(channel.getAddress(), loadFactor, commandFilter));
                        } catch (Exception e) {
                            throw new MembershipUpdateFailedException("Failed to notify my existence to " + member);
                        }
                    });

            asList(left).forEach(member -> members.remove(new ServiceMember<>(member, null, 0)));
        }
        currentView = view;
    }

    @Override
    public void suspect(Address suspected_mbr) {
        LOGGER.warn("Member is suspect: {}", suspected_mbr.toString());
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
        if (message.isSuccess()) {
            callbackWrapper.success(message.getReturnValue(serializer));
        } else {
            callbackWrapper.fail(message.getError(serializer));
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
                LOGGER.error("Could not dispatch command", e);
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
                LOGGER.error("Could not send reply", e1);
            }
        }
    }

    private void processJoinMessage(final Message message, final JoinMessage joinMessage) {
        String source = channel.getName(message.getSrc());
        if (source != null) {
            if (LOGGER.isInfoEnabled() && !message.getSrc().equals(channel.getAddress())) {
                LOGGER.info("{} joined with load factor: {}", source, joinMessage.getLoadFactor());
            }

            ServiceMember<Address> member = new ServiceMember<>(message.getSrc(), joinMessage.getCommandMessagePredicate(),
                    joinMessage.getLoadFactor());
            members.put(member.getIdentifier(), member);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Got a notwork of members: {}", members.values());
            }
            update(new HashSet<>(members.values()));
        } else {
            LOGGER.warn("Received join message from '{}', but a connection with the sender has been lost.",
                    message.getSrc().toString());
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

    public String getNodeName() {
        return channel.getName();
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


    @Override
    public Address getLocalEndpoint() {
        return channel.getAddress();
    }

    @Override
    public <C> void send(Address destination, CommandMessage<? extends C> command) throws Exception {
        channel.send(destination, new DispatchMessage(command, serializer, false));
    }

    @Override
    public <C, R> void send(Address destination, CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        callbackRepository.store(command.getIdentifier(), new CommandCallbackWrapper<>(destination, command, callback));
        try {
            channel.send(destination, new DispatchMessage(command, serializer, true));
        } catch (Exception e) {
            throw new CommandBusConnectorCommunicationException("Could not send command to remote", e);
        }
    }

    @Override
    public void updateMembers(Set<Address> newMembers) {
        Set<Address> lostMembers = new HashSet<>(currentMembers);
        lostMembers.removeAll(newMembers);
        lostMembers.forEach(callbackRepository::cancelCallbacks);

        currentMembers = new HashSet<>(newMembers);
    }

    @Override
    public int getLoadFactor() {
        return loadFactor;
    }
}
