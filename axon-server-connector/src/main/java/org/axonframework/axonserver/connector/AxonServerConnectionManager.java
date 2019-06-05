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
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.axonframework.axonserver.connector.util.UpstreamAwareStreamObserver;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.config.TagsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.net.ssl.SSLException;

/**
 * @author Marc Gathier
 */
public class AxonServerConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(AxonServerConnectionManager.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new AxonThreadFactory("AxonServerConnector") {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = super.newThread(r);
            thread.setDaemon(true);
            return thread;
        }
    });
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();
    private final List<Consumer<String>> disconnectListeners = new CopyOnWriteArrayList<>();
    private final List<Function<Consumer<String>, Consumer<String>>> reconnectInterceptors = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> reconnectListeners = new CopyOnWriteArrayList<>();
    private final AxonServerConfiguration connectInformation;
    private final TagsConfiguration tagsConfiguration;
    private final Map<String, Collection<Consumer<PlatformOutboundInstruction>>> handlers = new ConcurrentHashMap<>();
    private volatile boolean shutdown;
    private Map<String, StreamObserver<PlatformInboundInstruction>> instructionStreams = new ConcurrentHashMap<>();

    /**
     * Initializes the Axon Server Connection Manager with the connect information. The empty tags configuration is used
     * in this case.
     *
     * @param connectInformation Axon Server Configuration
     */
    public AxonServerConnectionManager(AxonServerConfiguration connectInformation) {
        this(connectInformation, new TagsConfiguration());
    }

    /**
     * Initializes the Axon Server Connection Manager with connect information and tags configuration.
     *
     * @param connectInformation Axon Server Configuration
     * @param tagsConfiguration  Tags Configuration
     */
    public AxonServerConnectionManager(AxonServerConfiguration connectInformation,
                                       TagsConfiguration tagsConfiguration) {
        this.connectInformation = connectInformation;
        this.tagsConfiguration = tagsConfiguration;
    }

    public Channel getChannel() {
        return getChannel(connectInformation.getContext());
    }

    public synchronized Channel getChannel(String context) {
        checkConnectionState(context);
        final ManagedChannel channel = channels.get(context);
        if (channel == null || channel.isShutdown()) {
            channels.remove(context);
            logger.info("Connecting using {}...", connectInformation.isSslEnabled() ? "TLS" : "unencrypted connection");
            boolean unavailable = false;
            for (NodeInfo nodeInfo : connectInformation.routingServers()) {
                ManagedChannel candidate = createChannel(nodeInfo.getHostName(), nodeInfo.getGrpcPort());
                PlatformServiceGrpc.PlatformServiceBlockingStub stub = PlatformServiceGrpc.newBlockingStub(candidate)
                                                                                          .withInterceptors(new ContextAddingInterceptor(connectInformation.getContext()), new TokenAddingInterceptor(connectInformation.getToken()));
                try {
                    ClientIdentification clientIdentification =
                            ClientIdentification.newBuilder()
                                                .setClientId(connectInformation.getClientId())
                                                .setComponentName(connectInformation.getComponentName())
                                                .putAllTags(tagsConfiguration.getTags())
                                                .build();

                    PlatformInfo clusterInfo = stub.getPlatformServer(clientIdentification);
                    if (isPrimary(nodeInfo, clusterInfo)) {
                        channels.put(context, candidate);
                    } else {
                        shutdown(candidate);
                        logger.info("Connecting to {} ({}:{})", clusterInfo.getPrimary().getNodeName(),
                                    clusterInfo.getPrimary().getHostName(),
                                    clusterInfo.getPrimary().getGrpcPort());
                        channels.put(context, createChannel(clusterInfo.getPrimary().getHostName(), clusterInfo.getPrimary().getGrpcPort()));
                    }
                    startInstructionStream(context, clusterInfo.getPrimary().getNodeName());
                    unavailable = false;
                    logger.info("Re-subscribing commands and queries");
                    reconnectListeners.forEach(rl -> rl.accept(context));
                    break;
                } catch (StatusRuntimeException sre) {
                    shutdown(candidate);
                    logger.warn("Connecting to AxonServer node {}:{} failed: {}", nodeInfo.getHostName(), nodeInfo.getGrpcPort(), sre.getMessage());
                    if (sre.getStatus().getCode().equals(Status.Code.UNAVAILABLE)) {
                        unavailable = true;
                    }
                }
            }
            if (unavailable) {
                if (!connectInformation.getSuppressDownloadMessage()) {
                    connectInformation.setSuppressDownloadMessage(true);
                    writeDownloadMessage();
                }
                scheduleReconnect(context, false);
                throw new AxonServerException(ErrorCode.CONNECTION_FAILED.errorCode(), "No connection to AxonServer available");
            } else if (!connectInformation.getSuppressDownloadMessage()) {
                connectInformation.setSuppressDownloadMessage(true);
            }
        }
        return intercepted(context, channels.get(context));
    }

    private Channel intercepted(String context, Channel candidate) {
        return ClientInterceptors.intercept(candidate,
                                            new TokenAddingInterceptor(connectInformation.getToken()),
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
            while ((read = in.read(buffer, 0, 1024)) >= 0) {
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
        return clusterInfo.getPrimary().getGrpcPort() == nodeInfo.getGrpcPort() && clusterInfo.getPrimary().getHostName().equals(nodeInfo.getHostName());
    }

    private ManagedChannel createChannel(String hostName, int port) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(hostName, port);

        if (connectInformation.getKeepAliveTime() > 0) {
            builder.keepAliveTime(connectInformation.getKeepAliveTime(), TimeUnit.MILLISECONDS)
                   .keepAliveTimeout(connectInformation.getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                   .keepAliveWithoutCalls(true);
        }

        if (connectInformation.getMaxMessageSize() > 0) {
            builder.maxInboundMessageSize(connectInformation.getMaxMessageSize());
        }
        if (connectInformation.isSslEnabled()) {
            try {
                if (connectInformation.getCertFile() != null) {
                    File certFile = new File(connectInformation.getCertFile());
                    if (!certFile.exists()) {
                        throw new RuntimeException("Certificate file " + connectInformation.getCertFile() + " does not exist");
                    }
                    SslContext sslContext = GrpcSslContexts.forClient()
                                                           .trustManager(new File(connectInformation.getCertFile()))
                                                           .build();
                    builder.sslContext(sslContext);
                }
            } catch (SSLException e) {
                throw new RuntimeException("Couldn't set up SSL context", e);
            }
        } else {
            builder.usePlaintext();
        }
        return builder.build();
    }

    private synchronized void startInstructionStream(String context, String name) {
        logger.debug("Start instruction stream to node {} for context {}", name, context);
        SynchronizedStreamObserver<PlatformInboundInstruction> inputStream = new SynchronizedStreamObserver<>(
                PlatformServiceGrpc.newStub(intercepted(
                        context, channels.get(context)))
                                   .openStream(new UpstreamAwareStreamObserver<PlatformOutboundInstruction>() {
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
                                           logger.debug("Lost instruction stream from {} - {}", name, throwable.getMessage());
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
                                           logger.info("Closed instruction stream to {}", name);
                                           disconnectListeners.forEach(rl -> rl.accept(context));
                                           scheduleReconnect(context, true);
                                       }
                                   }));
        ClientIdentification client = ClientIdentification.newBuilder()
                                                          .setClientId(connectInformation.getClientId())
                                                          .setComponentName(connectInformation.getComponentName())
                                                          .putAllTags(tagsConfiguration.getTags()).build();
        inputStream.onNext(PlatformInboundInstruction.newBuilder().setRegister(client).build());
        StreamObserver<PlatformInboundInstruction> existingStream = instructionStreams.put(context, inputStream);
        if (existingStream != null) {
            existingStream.onCompleted();
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
        }
    }

    public void addReconnectListener(String context, Runnable action) {
        addReconnectListener(c -> {
            if (context.equals(c)) {
                action.run();
            }
        });
    }

    public void addDisconnectListener(String context, Runnable action) {
        addDisconnectListener(c -> {
            if (context.equals(c)) {
                action.run();
            }
        });
    }

    public void addReconnectListener(Consumer<String> action) {
        reconnectListeners.add(action);
    }

    public void addDisconnectListener(Consumer<String> action) {
        disconnectListeners.add(action);
    }

    public void addReconnectInterceptor(Function<Consumer<String>, Consumer<String>> interceptor) {
        reconnectInterceptors.add(interceptor);
    }

    private synchronized void scheduleReconnect(String context, boolean immediate) {
        ScheduledFuture<?> reconnectTask = reconnectTasks.get(context);
        ManagedChannel channel = channels.get(context);
        if (!shutdown && (reconnectTask == null || reconnectTask.isDone())) {
            if (channel != null) {
                try {
                    channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {

                }
            }
            channels.remove(context);
            reconnectTasks.put(context, scheduler.schedule(() -> tryReconnect(context), immediate ? 100 : 5000, TimeUnit.MILLISECONDS));
        }
    }

    public StreamObserver<CommandProviderOutbound> getCommandStream(String context, StreamObserver<CommandProviderInbound> commandsFromRoutingServer) {
        return CommandServiceGrpc.newStub(getChannel(context))
                                 .openStream(commandsFromRoutingServer);
    }

    public StreamObserver<QueryProviderOutbound> getQueryStream(String context, StreamObserver<QueryProviderInbound> queryProviderInboundStreamObserver) {
        return QueryServiceGrpc.newStub(getChannel(context))
                               .openStream(queryProviderInboundStreamObserver);
    }

    @Deprecated
    public void onOutboundInstruction(PlatformOutboundInstruction.RequestCase requestCase, Consumer<PlatformOutboundInstruction> consumer) {
        onOutboundInstruction(getDefaultContext(), requestCase, consumer);
    }

    public void onOutboundInstruction(String context, PlatformOutboundInstruction.RequestCase requestCase, Consumer<PlatformOutboundInstruction> consumer) {
        this.handlers.computeIfAbsent(context, (rc) -> new LinkedList<>()).add(i -> {
            if (i.getRequestCase().equals(requestCase)) {
                consumer.accept(i);
            }
        });
    }

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
     * Disconnects any active connection, forcing a new connection to be established when one is requested.
     */
    public void disconnect(String context) {
        ManagedChannel channel = channels.get(context);
        if (channel != null) {
            shutdown(channel);
        }
    }

    /**
     * Disconnects any active connection, forcing a new connection to be established when one is requested.
     */
    public void disconnect() {
        channels.forEach((k, v) -> shutdown(v));
    }

    /**
     * Returns the name of the default context
     * @return the name of the default context
     */
    public String getDefaultContext() {
        return connectInformation.getContext();
    }
}
