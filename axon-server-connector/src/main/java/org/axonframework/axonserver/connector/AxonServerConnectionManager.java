/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.command.CommandProviderInbound;
import io.axoniq.axonserver.grpc.command.CommandProviderOutbound;
import io.axoniq.axonserver.grpc.command.CommandServiceGrpc;
import io.axoniq.axonserver.grpc.control.ClientIdentification;
import io.axoniq.axonserver.grpc.control.NodeInfo;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformInfo;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformServiceGrpc;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.GrpcBufferingInterceptor;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.axonframework.axonserver.connector.util.UpstreamAwareStreamObserver;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.config.TagsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.net.ssl.SSLException;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * The component which manages all the connections which an Axon client can establish with an Axon Server instance.
 * Does so by creating {@link Channel}s per context and providing them as the means to dispatch/receive messages.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerConnectionManager.class);

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, Collection<Consumer<PlatformOutboundInstruction>>> handlers = new ConcurrentHashMap<>();
    private Map<String, StreamObserver<PlatformInboundInstruction>> instructionStreams = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();
    private final List<Consumer<String>> reconnectListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> disconnectListeners = new CopyOnWriteArrayList<>();
    private final List<Function<Consumer<String>, Consumer<String>>> reconnectInterceptors =
            new CopyOnWriteArrayList<>();
    private volatile boolean shutdown;

    private final AxonServerConfiguration axonServerConfiguration;
    private final TagsConfiguration tagsConfiguration;
    private final ScheduledExecutorService scheduler;

    /**
     * Initializes the Axon Server Connection Manager with the connect information. An empty {@link TagsConfiguration}
     * is used in this case.
     *
     * @param axonServerConfiguration the configuration of Axon Server used to correctly establish connections
     * @deprecated in favor of using the {@link Builder} (with the convenience {@link #builder()} method)to instantiate
     * an Axon Server Connection Manager
     */
    @Deprecated
    public AxonServerConnectionManager(AxonServerConfiguration axonServerConfiguration) {
        this(axonServerConfiguration, new TagsConfiguration());
    }

    /**
     * Initializes the Axon Server Connection Manager with connect information and tags configuration.
     *
     * @param axonServerConfiguration the configuration of Axon Server used to correctly establish connections
     * @param tagsConfiguration       the TagsConfiguration used to add the tags of this instance as client information
     * @deprecated in favor of using the {@link Builder} (with the convenience {@link #builder()} method)to instantiate
     * an Axon Server Connection Manager
     */
    @Deprecated
    public AxonServerConnectionManager(AxonServerConfiguration axonServerConfiguration,
                                       TagsConfiguration tagsConfiguration) {
        this.axonServerConfiguration = axonServerConfiguration;
        this.tagsConfiguration = tagsConfiguration;
        this.scheduler = Executors.newScheduledThreadPool(
                1,
                new AxonThreadFactory("AxonServerConnector") {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = super.newThread(r);
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
    }

    /**
     * Instantiate a {@link AxonServerConnectionManager} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AxonServerConnectionManager} instance
     */
    protected AxonServerConnectionManager(Builder builder) {
        builder.validate();
        this.axonServerConfiguration = builder.axonServerConfiguration;
        this.tagsConfiguration = builder.tagsConfiguration;
        this.scheduler = builder.scheduler;
    }

    /**
     * Instantiate a Builder to be able to create an {@link AxonServerConnectionManager}.
     * <p>
     * The {@link TagsConfiguration} is defaulted to {@link TagsConfiguration#TagsConfiguration()} and the
     * {@link ScheduledExecutorService} defaults to an instance using a single thread with an {@link AxonThreadFactory}
     * tied to it. The {@link AxonServerConfiguration} is a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerConnectionManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a Channel for the default context, opening one if necessary.
     *
     * @return a Channel for the default context
     */
    public Channel getChannel() {
        return getChannel(getDefaultContext());
    }

    /**
     * Returns a Channel representing the connection for the given {@code context}, opening one if necessary.
     *
     * @param context the context for which to open a connection
     * @return the Channel corresponding to the given {@code context}
     */
    public synchronized Channel getChannel(String context) {
        checkConnectionState(context);
        final ManagedChannel channel = channels.get(context);

        if (channel == null || channel.isShutdown()) {
            logger.info("Connecting using {}...",
                        axonServerConfiguration.isSslEnabled() ? "TLS" : "unencrypted connection");

            channels.remove(context);
            boolean axonServerUnavailable = false;

            for (NodeInfo nodeInfo : axonServerConfiguration.routingServers()) {
                ManagedChannel candidate = createChannel(nodeInfo.getHostName(), nodeInfo.getGrpcPort());
                PlatformServiceGrpc.PlatformServiceBlockingStub stub =
                        PlatformServiceGrpc.newBlockingStub(candidate)
                                           .withInterceptors(
                                                   new ContextAddingInterceptor(axonServerConfiguration.getContext()),
                                                   new TokenAddingInterceptor(axonServerConfiguration.getToken())
                                           );
                try {
                    ClientIdentification clientIdentification =
                            ClientIdentification.newBuilder()
                                                .setClientId(axonServerConfiguration.getClientId())
                                                .setComponentName(axonServerConfiguration.getComponentName())
                                                .putAllTags(tagsConfiguration.getTags())
                                                .build();
                    PlatformInfo clusterInfo = stub.getPlatformServer(clientIdentification);

                    if (isPrimary(nodeInfo, clusterInfo)) {
                        channels.put(context, candidate);
                    } else {
                        shutdown(candidate);
                        logger.info("Connecting to [{}] ({}:{})",
                                    clusterInfo.getPrimary().getNodeName(),
                                    clusterInfo.getPrimary().getHostName(),
                                    clusterInfo.getPrimary().getGrpcPort());
                        channels.put(context, createChannel(
                                clusterInfo.getPrimary().getHostName(),
                                clusterInfo.getPrimary().getGrpcPort()
                        ));
                    }

                    startInstructionStream(context, clusterInfo.getPrimary().getNodeName(), clientIdentification);
                    axonServerUnavailable = false;
                    logger.info("Re-subscribing commands and queries");
                    reconnectListeners.forEach(
                            action -> scheduler.schedule(() -> action.accept(context), 100, TimeUnit.MILLISECONDS)
                    );
                    break;
                } catch (StatusRuntimeException sre) {
                    shutdown(candidate);
                    logger.warn(
                            "Connecting to AxonServer node [{}]:[{}] failed: {}",
                            nodeInfo.getHostName(), nodeInfo.getGrpcPort(), sre.getMessage()
                    );
                    if (sre.getStatus().getCode().equals(Status.Code.UNAVAILABLE)) {
                        axonServerUnavailable = true;
                    }
                }
            }

            if (axonServerUnavailable) {
                if (!axonServerConfiguration.getSuppressDownloadMessage()) {
                    axonServerConfiguration.setSuppressDownloadMessage(true);
                    writeDownloadMessage();
                }
                scheduleReconnect(context, false);
                throw new AxonServerException(ErrorCode.CONNECTION_FAILED.errorCode(),
                                              "No connection to AxonServer available");
            } else if (!axonServerConfiguration.getSuppressDownloadMessage()) {
                axonServerConfiguration.setSuppressDownloadMessage(true);
            }
        }

        return intercepted(context, channels.get(context));
    }

    private Channel intercepted(String context, Channel candidate) {
        return ClientInterceptors.intercept(candidate,
                                            new TokenAddingInterceptor(axonServerConfiguration.getToken()),
                                            new ContextAddingInterceptor(context));
    }

    private void checkConnectionState(String context) {
        if (shutdown) {
            throw new AxonServerException(ErrorCode.CONNECTION_FAILED.errorCode(), "Shutdown in progress");
        }

        ScheduledFuture<?> reconnectTask = reconnectTasks.get(context);
        if (reconnectTask != null && !reconnectTask.isDone()) {
            throw new AxonServerException(ErrorCode.CONNECTION_FAILED.errorCode(),
                                          "No connection to AxonServer available");
        }
    }

    private void writeDownloadMessage() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("axonserver_download.txt")) {
            byte[] buffer = new byte[1024];
            int read;
            while (in != null && (read = in.read(buffer, 0, 1024)) >= 0) {
                System.out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            logger.debug("Unable to write download advice. You're on your own now.", e);
        }
    }

    private void shutdown(ManagedChannel managedChannel) {
        try {
            managedChannel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Interrupted during shutdown");
        }
    }

    private boolean isPrimary(NodeInfo nodeInfo, PlatformInfo clusterInfo) {
        if (clusterInfo.getSameConnection()) {
            return true;
        }
        return clusterInfo.getPrimary().getGrpcPort() == nodeInfo.getGrpcPort() &&
                clusterInfo.getPrimary().getHostName().equals(nodeInfo.getHostName());
    }

    private ManagedChannel createChannel(String hostName, int port) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(hostName, port);

        if (axonServerConfiguration.getKeepAliveTime() > 0) {
            builder.keepAliveTime(axonServerConfiguration.getKeepAliveTime(), TimeUnit.MILLISECONDS)
                   .keepAliveTimeout(axonServerConfiguration.getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                   .keepAliveWithoutCalls(true);
        }

        if (axonServerConfiguration.getMaxMessageSize() > 0) {
            builder.maxInboundMessageSize(axonServerConfiguration.getMaxMessageSize());
        }

        if (axonServerConfiguration.isSslEnabled()) {
            try {
                if (axonServerConfiguration.getCertFile() != null) {
                    File certFile = new File(axonServerConfiguration.getCertFile());
                    if (!certFile.exists()) {
                        throw new RuntimeException(
                                "Certificate file [" + axonServerConfiguration.getCertFile() + "] does not exist"
                        );
                    }
                    SslContext sslContext = GrpcSslContexts.forClient()
                                                           .trustManager(new File(
                                                                   axonServerConfiguration.getCertFile()
                                                           ))
                                                           .build();
                    builder.sslContext(sslContext);
                }
            } catch (SSLException e) {
                throw new RuntimeException("Couldn't set up SSL context", e);
            }
        } else {
            builder.usePlaintext();
        }

        return builder.intercept(new GrpcBufferingInterceptor(axonServerConfiguration.getMaxGrpcBufferedMessages()))
                      .build();
    }

    private synchronized void startInstructionStream(String context,
                                                     String name,
                                                     ClientIdentification clientIdentification) {
        logger.debug("Start instruction stream to node [{}] for context [{}]", name, context);
        SynchronizedStreamObserver<PlatformInboundInstruction> inputStream = new SynchronizedStreamObserver<>(
                PlatformServiceGrpc.newStub(intercepted(
                        context, channels.get(context)
                )).openStream(new UpstreamAwareStreamObserver<PlatformOutboundInstruction>() {

                    @Override
                    public void onNext(PlatformOutboundInstruction messagePlatformOutboundInstruction) {
                        handlers.getOrDefault(context, Collections.emptyList())
                                .forEach(consumer -> consumer.accept(messagePlatformOutboundInstruction));

                        switch (messagePlatformOutboundInstruction.getRequestCase()) {
                            case NODE_NOTIFICATION:
                                logger.debug("Received: {}", messagePlatformOutboundInstruction.getNodeNotification());
                                break;
                            case REQUEST_RECONNECT:
                                Consumer<String> reconnect = (c) -> {
                                    disconnectListeners.forEach(rl -> rl.accept(c));
                                    getRequestStream().onCompleted();
                                    scheduleReconnect(context, true);
                                };
                                for (Function<Consumer<String>, Consumer<String>> interceptor : reconnectInterceptors) {
                                    reconnect = interceptor.apply(reconnect);
                                }
                                reconnect.accept(context);
                                break;
                            case REQUEST_NOT_SET:
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.warn("Lost instruction stream from [{}] - {}", name, throwable.getMessage());

                        disconnectListeners.forEach(rl -> rl.accept(context));
                        if (throwable instanceof StatusRuntimeException) {
                            StatusRuntimeException sre = (StatusRuntimeException) throwable;
                            if (sre.getStatus().getCode().equals(Status.Code.PERMISSION_DENIED)) {
                                return;
                            }
                        }
                        scheduleReconnect(context, true);
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("Closed instruction stream to [{}]", name);
                        disconnectListeners.forEach(rl -> rl.accept(context));
                        scheduleReconnect(context, true);
                    }
                })
        );

        inputStream.onNext(PlatformInboundInstruction.newBuilder().setRegister(clientIdentification).build());
        StreamObserver<PlatformInboundInstruction> existingStream = instructionStreams.put(context, inputStream);
        if (existingStream != null) {
            existingStream.onCompleted();
        }
    }

    private synchronized void scheduleReconnect(String context, boolean immediate) {
        ScheduledFuture<?> reconnectTask = reconnectTasks.get(context);
        ManagedChannel channel = channels.get(context);
        if (!shutdown && (reconnectTask == null || reconnectTask.isDone())) {
            if (channel != null) {
                try {
                    channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Ignoring this exception
                }
            }
            channels.remove(context);
            reconnectTasks.put(context,
                               scheduler.schedule(() -> tryReconnect(context),
                                                  immediate ? 100 : 5000,
                                                  TimeUnit.MILLISECONDS));
        }
    }

    private synchronized void tryReconnect(String context) {
        if (channels.containsKey(context) || shutdown) {
            return;
        }
        try {
            reconnectTasks.remove(context);
            getChannel(context);
        } catch (Exception ignored) {
            // Ignoring this exception
        }
    }

    /**
     * Registers a reconnect listener for the given {@code context} that executes given {@code action} when a
     * connection is re-established for the given {@code context}.
     *
     * @param context the context to register the reconnect listener for
     * @param action  the action to perform when the connection for the given {@code context} is re-established
     */
    public void addReconnectListener(String context, Runnable action) {
        addReconnectListener(c -> {
            if (context.equals(c)) {
                action.run();
            }
        });
    }

    /**
     * Registers a reconnect listener that executes given {@code action} when a connection is re-established. The
     * parameter of the invoked {@code action} is the name of the context for which the application was lost.
     *
     * @param action the action to perform when a connection is re-established
     */
    public void addReconnectListener(Consumer<String> action) {
        reconnectListeners.add(action);
    }

    /**
     * Registers a disconnect listener which executes a given {@code action} when the connection is disconnected for
     * given the {@code context}.
     *
     * @param context the context to register the disconnect listener for
     * @param action  the action to perform when the connection for the given {@code context} is disconnected
     */
    public void addDisconnectListener(String context, Runnable action) {
        addDisconnectListener(c -> {
            if (context.equals(c)) {
                action.run();
            }
        });
    }

    /**
     * Registers a disconnect listener which executes a given {@code action} when a connection is disconnected. The
     * parameter of the invoked {@code action} is the name of the context for which the connection was lost.
     *
     * @param action the action to perform when a connection is disconnected
     */
    public void addDisconnectListener(Consumer<String> action) {
        disconnectListeners.add(action);
    }

    /**
     * Registers an interceptor that may alter the behavior on an incoming "reconnect request" from the server. This
     * request occurs when AxonServer requests connections to be established on another node than the current one.
     * <p>
     * The registered interceptor may deny these requests by blocking the call to the input Consumer.
     *
     * @param interceptor a function altering the original reconnect request
     */
    public void addReconnectInterceptor(Function<Consumer<String>, Consumer<String>> interceptor) {
        reconnectInterceptors.add(interceptor);
    }

    /**
     * Opens a Stream for incoming commands from AxonServer in the given {@code context}. While either reuse an existing
     * Channel or create a new one.
     *
     * @param context              the context to open the command stream for
     * @param inboundCommandStream the callback to invoke with incoming command messages
     * @return the stream to send command subscription instructions and execution results to AxonServer over
     */
    public StreamObserver<CommandProviderOutbound> getCommandStream(String context,
                                                                    StreamObserver<CommandProviderInbound> inboundCommandStream) {
        return CommandServiceGrpc.newStub(getChannel(context))
                                 .openStream(inboundCommandStream);
    }


    /**
     * Opens a Stream for incoming queries from AxonServer in the given {@code context}. While either reuse an existing
     * Channel or create a new one.
     *
     * @param context            the context to open the query stream for
     * @param inboundQueryStream the callback to invoke with incoming query messages
     * @return the stream to send query subscription instructions and query responses to AxonServer over
     */
    public StreamObserver<QueryProviderOutbound> getQueryStream(String context,
                                                                StreamObserver<QueryProviderInbound> inboundQueryStream) {
        return QueryServiceGrpc.newStub(getChannel(context))
                               .openStream(inboundQueryStream);
    }

    /**
     * Registers a handler to handle instructions from AxonServer on the default context.
     *
     * @param requestCase the type of instruction to respond to
     * @param consumer    the handler of the instruction
     * @deprecated in favor of {@link #onOutboundInstruction(String, PlatformOutboundInstruction.RequestCase, Consumer)}
     * as the context should be specified on any outbound instruction
     */
    @Deprecated
    public void onOutboundInstruction(PlatformOutboundInstruction.RequestCase requestCase,
                                      Consumer<PlatformOutboundInstruction> consumer) {
        onOutboundInstruction(getDefaultContext(), requestCase, consumer);
    }

    /**
     * Registers a handler to handle instructions from AxonServer.
     *
     * @param context     the context for which the instruction is intended
     * @param requestCase the type of instruction to respond to
     * @param consumer    the handler of the instruction
     */
    public void onOutboundInstruction(String context,
                                      PlatformOutboundInstruction.RequestCase requestCase,
                                      Consumer<PlatformOutboundInstruction> consumer) {
        this.handlers.computeIfAbsent(context, (rc) -> new LinkedList<>()).add(i -> {
            if (i.getRequestCase().equals(requestCase)) {
                consumer.accept(i);
            }
        });
    }

    /**
     * Send the given {@code instruction} for given {@code context} to AxonServer. Will not send anything if no Channel
     * can be created or found for the given {@code context}.
     *
     * @param context     the context for which the instruction is intended
     * @param instruction the message containing information for AxonServer to process
     */
    public void send(String context, PlatformInboundInstruction instruction) {
        if (getChannel(context) != null) {
            instructionStreams.get(context).onNext(instruction);
        }
    }

    /**
     * Stops the Connection Manager, closing any active connections and preventing new connections from being created.
     */
    public void shutdown() {
        shutdown = true;
        disconnect();
        scheduler.shutdown();
    }

    /**
     * Disconnects any active connection for the given {@code context}, forcing a new connection to be established when
     * one is requested.
     *
     * @param context the context for which the connection must be disconnected
     */
    public void disconnect(String context) {
        ManagedChannel channel = channels.get(context);
        if (channel != null) {
            shutdown(channel);
        }
    }

    /**
     * Disconnects any active connections, forcing a new connection to be established when one is requested.
     */
    public void disconnect() {
        channels.forEach((k, v) -> shutdown(v));
    }

    /**
     * Returns the name of the default context of this application.
     *
     * @return the name of the default context of this application
     */
    public String getDefaultContext() {
        return axonServerConfiguration.getContext();
    }

    /**
     * Builder class to instantiate an {@link AxonServerConnectionManager}.
     * <p>
     * The {@link TagsConfiguration} is defaulted to {@link TagsConfiguration#TagsConfiguration()} and the
     * {@link ScheduledExecutorService} defaults to an instance using a single thread with an {@link AxonThreadFactory}
     * tied to it. The {@link AxonServerConfiguration} is a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private static final int DEFAULT_POOL_SIZE = 1;

        private AxonServerConfiguration axonServerConfiguration;
        private TagsConfiguration tagsConfiguration = new TagsConfiguration();
        private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                DEFAULT_POOL_SIZE,
                new AxonThreadFactory(AxonServerConnectionManager.class.getSimpleName()) {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = super.newThread(r);
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );

        /**
         * Sets the {@link AxonServerConfiguration} used to correctly configure connections between Axon clients and
         * Axon Server.
         *
         * @param axonServerConfiguration an {@link AxonServerConfiguration} used to correctly configure the connections
         *                                created by an {@link AxonServerConnectionManager} instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder axonServerConfiguration(AxonServerConfiguration axonServerConfiguration) {
            assertNonNull(axonServerConfiguration, "AxonServerConfiguration may not be null");
            this.axonServerConfiguration = axonServerConfiguration;
            return this;
        }

        /**
         * Sets the {@link TagsConfiguration} used to add the tags of this Axon instance as client information when
         * setting up a channel. Can for example be used to introduce labelling of your client applications for correct
         * distribution in an Axon Server cluster. Defaults to {@link TagsConfiguration#TagsConfiguration()}, thus an
         * empty set of tags.
         *
         * @param tagsConfiguration a {@link TagsConfiguration} to add the tags of this Axon instance as client
         *                          information when setting up a channel
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder tagsConfiguration(TagsConfiguration tagsConfiguration) {
            assertNonNull(tagsConfiguration, "TagsConfiguration may not be null");
            this.tagsConfiguration = tagsConfiguration;
            return this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} used to schedule connection attempts given certain success and/or
         * failure scenarios of a channel. Defaults to a ScheduledExecutorService with a single thread, using the
         * {@link AxonThreadFactory} to instantiate the threads.
         *
         * @param scheduler a {@link ScheduledExecutorService} used to schedule connection attempts given certain
         *                  success and/or failure scenarios of a channel
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scheduler(ScheduledExecutorService scheduler) {
            assertNonNull(scheduler, "ScheduledExecutorService may not be null");
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Initializes a {@link AxonServerConnectionManager} as specified through this Builder.
         *
         * @return a {@link AxonServerConnectionManager} as specified through this Builder
         */
        public AxonServerConnectionManager build() {
            return new AxonServerConnectionManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(
                    axonServerConfiguration, "The AxonServerConfiguration is a hard requirement and should be provided"
            );
        }
    }
}
