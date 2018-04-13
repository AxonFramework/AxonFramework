/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client;

import io.axoniq.axonhub.client.util.ContextAddingInterceptor;
import io.axoniq.axonhub.client.util.TokenAddingInterceptor;
import io.axoniq.axonhub.grpc.CommandProviderInbound;
import io.axoniq.axonhub.grpc.CommandProviderOutbound;
import io.axoniq.axonhub.grpc.CommandServiceGrpc;
import io.axoniq.axonhub.grpc.QueryProviderInbound;
import io.axoniq.axonhub.grpc.QueryProviderOutbound;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import io.axoniq.platform.grpc.ClientIdentification;
import io.axoniq.platform.grpc.NodeInfo;
import io.axoniq.platform.grpc.PlatformInboundInstruction;
import io.axoniq.platform.grpc.PlatformInfo;
import io.axoniq.platform.grpc.PlatformOutboundInstruction;
import io.axoniq.platform.grpc.PlatformServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
import javax.net.ssl.SSLException;

/**
 * @author Marc Gathier
 */
public class PlatformConnectionManager {
    private final static Logger logger = LoggerFactory.getLogger(PlatformConnectionManager.class);

    private volatile ManagedChannel channel;
    private volatile StreamObserver<PlatformInboundInstruction> inputStream;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile ScheduledFuture<?> reconnectTask;
    private final List<Runnable> disconnectListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> reconnectListeners = new CopyOnWriteArrayList<>();
    private final AxonHubConfiguration connectInformation;
    private final Map<PlatformOutboundInstruction.RequestCase, Collection<Consumer<PlatformOutboundInstruction>>> handlers = new EnumMap<>(PlatformOutboundInstruction.RequestCase.class);

    public PlatformConnectionManager(AxonHubConfiguration connectInformation) {
        this.connectInformation = connectInformation;
    }

    public synchronized Channel getChannel() {
        if( channel == null) {
            logger.info("Connecting {}using SSL...", connectInformation.isSslEnabled()?"":"not ");
            boolean unavailable = false;
            for(NodeInfo nodeInfo : connectInformation.routingServers()) {
                ManagedChannel candidate = createChannel( nodeInfo.getHostName(), nodeInfo.getGrpcPort());
                PlatformServiceGrpc.PlatformServiceBlockingStub stub = PlatformServiceGrpc.newBlockingStub(candidate)
                        .withInterceptors(new ContextAddingInterceptor(connectInformation.getContext()), new TokenAddingInterceptor(connectInformation.getToken()));
                try {
                    PlatformInfo clusterInfo = stub.getPlatformServer(ClientIdentification.newBuilder()
                            .setClientName(connectInformation.getClientName())
                            .setComponentName(connectInformation.getComponentName())
                            .build());
                    if(isPrimary(nodeInfo, clusterInfo)) {
                        channel = candidate;
                    } else {
                        candidate.shutdownNow();
                        logger.info("Connecting to {} ({}:{})", clusterInfo.getPrimary().getNodeName(), clusterInfo.getPrimary().getHostName(), clusterInfo.getPrimary().getGrpcPort());
                        channel = createChannel(clusterInfo.getPrimary().getHostName(), clusterInfo.getPrimary().getGrpcPort());
                                ManagedChannelBuilder.forAddress(clusterInfo.getPrimary().getHostName(), clusterInfo.getPrimary().getGrpcPort()).usePlaintext(true).build();
                    }
                    startInstructionStream();
                    unavailable = false;
                    break;
                } catch( StatusRuntimeException sre) {
                    logger.warn("Connecting to AxonHub node {}:{} failed: {}", nodeInfo.getHostName(), nodeInfo.getGrpcPort(), sre.getMessage());
                    if( sre.getStatus().getCode().equals(Status.Code.UNAVAILABLE)) {
                        unavailable = true;
                    }
                }
            }
            if( unavailable) {
                scheduleReconnect();
                throw new RuntimeException("No connection to AxonHub available");
            }
        }
        return channel;
    }

    private boolean isPrimary(NodeInfo nodeInfo, PlatformInfo clusterInfo) {
        return clusterInfo.getPrimary().getGrpcPort() == nodeInfo.getGrpcPort() && clusterInfo.getPrimary().getHostName().equals(nodeInfo.getHostName());
    }

    private ManagedChannel createChannel(String hostName, int port) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(hostName, port);
        if (connectInformation.isSslEnabled()) {
            try {
                if( connectInformation.getCertFile() == null) throw new RuntimeException("SSL enabled but no certificate file specified");
                File certFile = new File(connectInformation.getCertFile());
                if( ! certFile.exists()) {
                    throw new RuntimeException("Certificate file " + connectInformation.getCertFile() + " does not exist");
                }
                SslContext sslContext = GrpcSslContexts.forClient()
                        .trustManager(new File(connectInformation.getCertFile()))
                        .build();
                builder.sslContext(sslContext);
            } catch (SSLException e) {
                throw new RuntimeException("Couldn't set up SSL context", e);
            }
        } else {
            builder.usePlaintext(true);
        }
        return builder.build();
    }

    private synchronized void startInstructionStream() {
        logger.debug("Start instruction stream");
        inputStream = PlatformServiceGrpc.newStub(channel)
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
                                disconnectListeners.forEach(Runnable::run);
                                inputStream.onCompleted();
                                scheduleReconnect();
                                break;
                            case REQUEST_NOT_SET:
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.debug("Lost instruction stream - {}", throwable.getMessage());
                        disconnectListeners.forEach(Runnable::run);
                        if( throwable instanceof StatusRuntimeException) {
                            StatusRuntimeException sre = (StatusRuntimeException)throwable;
                            if( sre.getStatus().getCode().equals(Status.Code.PERMISSION_DENIED)) return;
                        }
                        scheduleReconnect();
                    }

                    @Override
                    public void onCompleted() {
                        logger.debug("Closed instruction stream");
                        disconnectListeners.forEach(Runnable::run);
                        scheduleReconnect();
                    }
                });
        inputStream.onNext(PlatformInboundInstruction.newBuilder().setRegister(ClientIdentification.newBuilder()
                .setClientName(connectInformation.getClientName())
                .setComponentName(connectInformation.getComponentName())
        ).build());
    }

    private void tryReconnect() {
        try {
            reconnectTask = null;
            getChannel();
            reconnectListeners.forEach(Runnable::run);
        } catch (Exception ignored) {

        }
    }

    public void addReconnectListener(Runnable action) {
        reconnectListeners.add(action);
    }

    public void addDisconnectListener(Runnable action) {
        disconnectListeners.add(action);
    }

    public synchronized void scheduleReconnect() {
        channel = null;
        if( reconnectTask == null || reconnectTask.isDone()) {
            reconnectTask = scheduler.schedule(this::tryReconnect, 1, TimeUnit.SECONDS);
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
}
