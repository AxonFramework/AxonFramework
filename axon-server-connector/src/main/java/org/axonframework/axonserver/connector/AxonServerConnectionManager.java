/*
 * Copyright (c) 2010-2018. Axon Framework
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
import io.axoniq.axonserver.grpc.control.*;
import io.axoniq.axonserver.grpc.query.QueryProviderInbound;
import io.axoniq.axonserver.grpc.query.QueryProviderOutbound;
import io.axoniq.axonserver.grpc.query.QueryServiceGrpc;
import io.grpc.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.axonframework.common.AxonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Marc Gathier
 */
public class AxonServerConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(AxonServerConnectionManager.class);

    private volatile ManagedChannel channel;
    private volatile boolean shutdown;
    private volatile StreamObserver<PlatformInboundInstruction> inputStream;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new AxonThreadFactory("AxonServerConnector") {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = super.newThread(r);
            thread.setDaemon(true);
            return thread;
        }
    });
    private volatile ScheduledFuture<?> reconnectTask;
    private final List<Runnable> disconnectListeners = new CopyOnWriteArrayList<>();
    private final List<Function<Runnable, Runnable>> reconnectInterceptors = new CopyOnWriteArrayList<>();
    private final List<Runnable> reconnectListeners = new CopyOnWriteArrayList<>();
    private final AxonServerConfiguration connectInformation;
    private final Map<PlatformOutboundInstruction.RequestCase, Collection<Consumer<PlatformOutboundInstruction>>> handlers = new EnumMap<>(PlatformOutboundInstruction.RequestCase.class);

    public AxonServerConnectionManager(AxonServerConfiguration connectInformation) {
        this.connectInformation = connectInformation;
    }

    public synchronized Channel getChannel() {
        checkConnectionState();
        if( channel == null || channel.isShutdown()) {
            channel = null;
            logger.info("Connecting using {}...", connectInformation.isSslEnabled()?"TLS":"unencrypted connection");
            boolean unavailable = false;
            for(NodeInfo nodeInfo : connectInformation.routingServers()) {
                ManagedChannel candidate = createChannel( nodeInfo.getHostName(), nodeInfo.getGrpcPort());
                PlatformServiceGrpc.PlatformServiceBlockingStub stub = PlatformServiceGrpc.newBlockingStub(candidate)
                        .withInterceptors(new ContextAddingInterceptor(connectInformation.getContext()), new TokenAddingInterceptor(connectInformation.getToken()));
                try {
                    PlatformInfo clusterInfo = stub.getPlatformServer(ClientIdentification.newBuilder()
                            .setClientId(connectInformation.getClientId())
                            .setComponentName(connectInformation.getComponentName())
                            .build());
                    if(isPrimary(nodeInfo, clusterInfo)) {
                        channel = candidate;
                    } else {
                        shutdown(candidate);
                        logger.info("Connecting to {} ({}:{})", clusterInfo.getPrimary().getNodeName(),
                                    clusterInfo.getPrimary().getHostName(),
                                    clusterInfo.getPrimary().getGrpcPort());
                        channel = createChannel(clusterInfo.getPrimary().getHostName(), clusterInfo.getPrimary().getGrpcPort());
                    }
                    startInstructionStream(clusterInfo.getPrimary().getNodeName());
                    unavailable = false;
                    logger.info("Re-subscribing commands and queries");
                    reconnectListeners.forEach(Runnable::run);
                    break;
                } catch( StatusRuntimeException sre) {
                    shutdown(candidate);
                    logger.warn("Connecting to AxonServer node {}:{} failed: {}", nodeInfo.getHostName(), nodeInfo.getGrpcPort(), sre.getMessage());
                    if( sre.getStatus().getCode().equals(Status.Code.UNAVAILABLE)) {
                        unavailable = true;
                    }
                }
            }
            if( unavailable) {
                if(!connectInformation.getSuppressDownloadMessage()) {
                    connectInformation.setSuppressDownloadMessage(true);
                    writeDownloadMessage();
                }
                scheduleReconnect(false);
                throw new AxonServerException(ErrorCode.CONNECTION_FAILED.errorCode(), "No connection to AxonServer available");
            }
        }
        return channel;
    }

    private void checkConnectionState() {
        if (shutdown) {
            throw new AxonServerException(ErrorCode.CONNECTION_FAILED.errorCode(), "Shutdown in progress");
        }

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
        if( clusterInfo.getSameConnection()) return true;
        return clusterInfo.getPrimary().getGrpcPort() == nodeInfo.getGrpcPort() && clusterInfo.getPrimary().getHostName().equals(nodeInfo.getHostName());
    }

    private ManagedChannel createChannel(String hostName, int port) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(hostName, port);

        if( connectInformation.getKeepAliveTime() > 0) {
            builder.keepAliveTime(connectInformation.getKeepAliveTime(), TimeUnit.MILLISECONDS)
                   .keepAliveTimeout(connectInformation.getKeepAliveTimeout(), TimeUnit.MILLISECONDS)
                   .keepAliveWithoutCalls(true);
        }

        if( connectInformation.getMaxMessageSize() > 0) {
            builder.maxInboundMessageSize(connectInformation.getMaxMessageSize());
        }
        if (connectInformation.isSslEnabled()) {
            try {
                if( connectInformation.getCertFile() != null) {
                    File certFile = new File(connectInformation.getCertFile());
                    if( ! certFile.exists()) {
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

    private synchronized void startInstructionStream(String name) {
        logger.debug("Start instruction stream to {}", name);
        inputStream = new SynchronizedStreamObserver<>(PlatformServiceGrpc.newStub(channel)
                                                                        .withInterceptors(new ContextAddingInterceptor(connectInformation.getContext()), new TokenAddingInterceptor(connectInformation.getToken()))
                                                                        .openStream(new StreamObserver<PlatformOutboundInstruction>() {
                    @Override
                    public void onNext(PlatformOutboundInstruction messagePlatformOutboundInstruction) {
                        handlers.getOrDefault(messagePlatformOutboundInstruction.getRequestCase(), new ArrayDeque<>())
                                .forEach(consumer -> consumer.accept(messagePlatformOutboundInstruction));

                        switch (messagePlatformOutboundInstruction.getRequestCase()) {
                            case NODE_NOTIFICATION:
                                logger.debug("Received: {}", messagePlatformOutboundInstruction.getNodeNotification());
                                break;
                            case REQUEST_RECONNECT:
                                Runnable reconnect = () -> {
                                    disconnectListeners.forEach(Runnable::run);
                                    inputStream.onCompleted();
                                    scheduleReconnect(true);
                                };
                                for (Function<Runnable,Runnable> interceptor : reconnectInterceptors) {
                                    reconnect = interceptor.apply(reconnect);
                                }
                                reconnect.run();
                                break;
                            case REQUEST_NOT_SET:

                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.debug("Lost instruction stream from {} - {}", name, throwable.getMessage());
                        disconnectListeners.forEach(Runnable::run);
                        if( throwable instanceof StatusRuntimeException) {
                            StatusRuntimeException sre = (StatusRuntimeException)throwable;
                            if( sre.getStatus().getCode().equals(Status.Code.PERMISSION_DENIED)) return;
                        }
                        scheduleReconnect(true);
                    }

                    @Override
                    public void onCompleted() {
                        logger.warn("Closed instruction stream to {}", name);
                        disconnectListeners.forEach(Runnable::run);
                        scheduleReconnect(true);
                    }
                }));
        inputStream.onNext(PlatformInboundInstruction.newBuilder().setRegister(ClientIdentification.newBuilder()
                .setClientId(connectInformation.getClientId())
                .setComponentName(connectInformation.getComponentName())
        ).build());
    }

    private synchronized void tryReconnect() {
        if( channel != null || shutdown) return;
        try {
            reconnectTask = null;
            getChannel();
        } catch (Exception ignored) {
        }
    }

    public void addReconnectListener(Runnable action) {
        reconnectListeners.add(action);
    }

    public void addDisconnectListener(Runnable action) {
        disconnectListeners.add(action);
    }

    public void addReconnectInterceptor(Function<Runnable, Runnable> interceptor){
        reconnectInterceptors.add(interceptor);
    }

    private synchronized void scheduleReconnect(boolean immediate) {
        if( !shutdown && (reconnectTask == null || reconnectTask.isDone())) {
            if( channel != null) {
                try {
                    channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
                } catch(Exception ignored) {

                }
            }
            channel = null;
            reconnectTask = scheduler.schedule(this::tryReconnect, immediate ? 100 : 5000, TimeUnit.MILLISECONDS);
        }
    }

    public StreamObserver<CommandProviderOutbound> getCommandStream(StreamObserver<CommandProviderInbound> commandsFromRoutingServer, ClientInterceptor[] interceptors) {
        return CommandServiceGrpc.newStub(getChannel())
                .withInterceptors(interceptors)
                .openStream(commandsFromRoutingServer);
    }

    public StreamObserver<QueryProviderOutbound> getQueryStream(StreamObserver<QueryProviderInbound> queryProviderInboundStreamObserver, ClientInterceptor[] interceptors) {
        return QueryServiceGrpc.newStub(getChannel())
                .withInterceptors(interceptors)
                .openStream(queryProviderInboundStreamObserver);
    }

    public void onOutboundInstruction(PlatformOutboundInstruction.RequestCase requestCase, Consumer<PlatformOutboundInstruction> consumer){
        Collection<Consumer<PlatformOutboundInstruction>> consumers = this.handlers.computeIfAbsent(requestCase, (rc) -> new LinkedList<>());
        consumers.add(consumer);
    }

    public void send(PlatformInboundInstruction instruction){
        inputStream.onNext(instruction);
    }

    public void shutdown() {
        if (channel != null) {
            shutdown(channel);
        }
        shutdown = true;
        scheduler.shutdown();
    }
}
