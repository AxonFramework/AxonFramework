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

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionAck;
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
import org.axonframework.axonserver.connector.util.AxonFrameworkVersionResolver;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.GrpcBufferingInterceptor;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.axonframework.axonserver.connector.util.UpstreamAwareStreamObserver;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.config.TagsConfiguration;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.*;
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
    private final Handlers<PlatformOutboundInstruction.RequestCase, BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>>> handlers = new DefaultHandlers<>();
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
    private final Supplier<String> axonFrameworkVersionResolver;
    private final Function<UpstreamAwareStreamObserver<PlatformOutboundInstruction>, StreamObserver<PlatformInboundInstruction>> requestStreamFactory;
    private final InstructionAckSource<PlatformInboundInstruction> instructionAckSource;

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
        this.axonFrameworkVersionResolver = new AxonFrameworkVersionResolver();
        this.requestStreamFactory = os -> (StreamObserver<PlatformInboundInstruction>) os.getRequestStream();
        this.instructionAckSource = new DefaultInstructionAckSource<>(ack -> PlatformInboundInstruction.newBuilder()
                                                                                                       .setAck(ack)
                                                                                                       .build());
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
        this.axonFrameworkVersionResolver = builder.axonFrameworkVersionResolver;
        this.requestStreamFactory = builder.requestStreamFactory;
        this.instructionAckSource = builder.instructionAckSource;
        onOutboundInstruction(NODE_NOTIFICATION,
                              (instruction, stream) -> logger.debug("Received: {}", instruction.getNodeNotification()));
        handlers.register(ACK, (instruction, stream) -> {
            if (isUnsupportedInstructionErrorAck(instruction.getAck())) {
                logger.warn("Unsupported instruction sent to the server. {}", instruction.getAck());
            } else {
                logger.trace("Received instruction ack {}.", instruction.getAck());
            }
        });
    }

    private boolean isUnsupportedInstructionErrorAck(InstructionAck instructionResult) {
        return instructionResult.hasError()
                && instructionResult.getError().getErrorCode().equals(ErrorCode.UNSUPPORTED_INSTRUCTION.errorCode());
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

            ClientIdentification clientIdentification =
                    ClientIdentification.newBuilder()
                                        .setClientId(axonServerConfiguration.getClientId())
                                        .setComponentName(axonServerConfiguration.getComponentName())
                                        .putAllTags(tagsConfiguration.getTags())
                                        .setVersion(axonFrameworkVersionResolver.get())
                                        .build();

            ManagedChannel previousChannel = channels.remove(context);
            if (previousChannel != null && !previousChannel.isTerminated()) {
                logger.info("Channel re-opened. Shutting down previous Channel.");
                previousChannel.shutdownNow();
            }
            boolean axonServerUnavailable = false;

            for (NodeInfo nodeInfo : axonServerConfiguration.routingServers()) {
                ManagedChannel candidate = createChannel(nodeInfo.getHostName(), nodeInfo.getGrpcPort());
                PlatformServiceGrpc.PlatformServiceBlockingStub stub =
                        PlatformServiceGrpc.newBlockingStub(candidate)
                                           .withDeadlineAfter(axonServerConfiguration.getConnectTimeout(), TimeUnit.MILLISECONDS)
                                           .withInterceptors(
                                                   new ContextAddingInterceptor(axonServerConfiguration.getContext()),
                                                   new TokenAddingInterceptor(axonServerConfiguration.getToken())
                                           );
                try {
                    logger.info("Requesting connection details from {}:{}",
                                nodeInfo.getHostName(), nodeInfo.getGrpcPort());
                    PlatformInfo clusterInfo = stub.getPlatformServer(clientIdentification);
                    logger.debug("Received PlatformInfo suggesting [{}] ({}:{}), {}",
                                 clusterInfo.getPrimary().getNodeName(),
                                 clusterInfo.getPrimary().getHostName(),
                                 clusterInfo.getPrimary().getGrpcPort(),
                                 clusterInfo.getSameConnection() ? "reusing existing connection": "using new connection");
                    if (isPrimary(nodeInfo, clusterInfo)) {
                        logger.info("Reusing existing channel");
                        channels.put(context, candidate);
                    } else {
                        shutdownNow(candidate);
                        logger.info("Connecting to [{}] ({}:{})",
                                    clusterInfo.getPrimary().getNodeName(),
                                    clusterInfo.getPrimary().getHostName(),
                                    clusterInfo.getPrimary().getGrpcPort());
                        channels.put(context, createChannel(
                                clusterInfo.getPrimary().getHostName(),
                                clusterInfo.getPrimary().getGrpcPort()
                        ));
                    }
                    onOutboundInstruction(context,
                                          REQUEST_RECONNECT,
                                          (instruction, requestStream) -> onRequestReconnect(context, requestStream));
                    startInstructionStream(context, clusterInfo.getPrimary().getNodeName(), clientIdentification);
                    axonServerUnavailable = false;
                    logger.info("Re-subscribing commands and queries");
                    notifyConnectionChange(reconnectListeners, context);
                    break;
                } catch (StatusRuntimeException sre) {
                    shutdownNow(candidate);
                    logger.warn(
                            "Connecting to AxonServer node [{}]:[{}] failed: {}",
                            nodeInfo.getHostName(), nodeInfo.getGrpcPort(), sre.getMessage()
                    );
                    axonServerUnavailable = true;
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

    private void onRequestReconnect(String context, StreamObserver<PlatformInboundInstruction> requestStream) {
        Consumer<String> reconnect = (c) -> {
            notifyConnectionChange(disconnectListeners, c);
            requestStream.onCompleted();
            scheduleReconnect(context, true);
        };
        for (Function<Consumer<String>, Consumer<String>> interceptor : reconnectInterceptors) {
            reconnect = interceptor.apply(reconnect);
        }
        reconnect.accept(context);
    }

    private void notifyConnectionChange(List<Consumer<String>> listeners, String context) {
        listeners.forEach(action -> scheduler.execute(() -> action.accept(context)));
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

    private void shutdownNow(ManagedChannel managedChannel) {
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
                getPlatformStream(context, new UpstreamAwareStreamObserver<PlatformOutboundInstruction>() {

                    @Override
                    public void onNext(PlatformOutboundInstruction messagePlatformOutboundInstruction) {
                        Collection<BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>>> defaultHandlers = Collections
                                .singleton((poi, stream) -> instructionAckSource
                                        .sendUnsupportedInstruction(poi.getInstructionId(),
                                                                    axonServerConfiguration.getClientId(),
                                                                    requestStreamFactory.apply(this)));
                        handlers.getOrDefault(context,
                                              messagePlatformOutboundInstruction.getRequestCase(),
                                              defaultHandlers)
                                .forEach(consumer -> consumer.accept(messagePlatformOutboundInstruction,
                                                                     requestStreamFactory.apply(this)));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.warn("Lost instruction stream from [{}] - {}", name, throwable.getMessage());
                        completeRequestStream();
                        notifyConnectionChange(disconnectListeners, context);
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
                        completeRequestStream();
                        notifyConnectionChange(disconnectListeners, context);
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
        if (!shutdown && (reconnectTask == null || reconnectTask.isDone())) {
            ManagedChannel channel = channels.remove(context);
            if (channel != null) {
                try {
                    channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Ignoring this exception
                }
            }
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
     * Opens a Stream for platform instructions in given {@code context}. It assumes that channel with given {@code
     * context} is already opened.
     *
     * @param context                   the context
     * @param outboundInstructionStream the callback to invoke outbounding instructions
     * @return the stream to send instructions
     */
    public StreamObserver<PlatformInboundInstruction> getPlatformStream(String context,
                                                                        StreamObserver<PlatformOutboundInstruction> outboundInstructionStream) {
        return PlatformServiceGrpc.newStub(intercepted(context, channels.get(context)))
                                  .openStream(outboundInstructionStream);
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
        onOutboundInstruction(context, requestCase, (i, s) -> consumer.accept(i));
    }

    /**
     * Registers a handler to handle instructions from AxonServer.
     *
     * @param requestCase the type of instruction to respond to
     * @param handler     the handler of the instruction
     */
    public void onOutboundInstruction(PlatformOutboundInstruction.RequestCase requestCase,
                                      BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> handler) {
        this.handlers.register(requestCase, wrapWithConfirmation(handler));
    }

    /**
     * Registers a handler to handle instructions from AxonServer.
     *
     * @param context the context
     * @param handler the handler of the instruction
     */
    public void onOutboundInstruction(String context,
                                      BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> handler) {
        this.handlers.register(context, wrapWithConfirmation(handler));
    }

    /**
     * Registers a handler to handle instructions from AxonServer.
     *
     * @param handler the handler of the instruction
     */
    public void onOutboundInstruction(
            BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> handler) {
        this.handlers.register(wrapWithConfirmation(handler));
    }

    /**
     * Registers a handler to handle instructions from AxonServer.
     *
     * @param context     the context
     * @param requestCase the type of instruction to respond to
     * @param handler     the handler of the instruction
     */
    public void onOutboundInstruction(String context,
                                      PlatformOutboundInstruction.RequestCase requestCase,
                                      BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> handler) {
        this.handlers.register(context, requestCase, wrapWithConfirmation(handler));
    }

    /**
     * Registers a handler to handle instructions from AxonServer.
     *
     * @param handlerSelector selects a handler based on context and request case
     * @param handler         the handler of the instruction
     */
    public void onOutboundInstruction(
            BiPredicate<String, PlatformOutboundInstruction.RequestCase> handlerSelector,
            BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> handler) {
        this.handlers.register(handlerSelector, wrapWithConfirmation(handler));
    }

    private BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> wrapWithConfirmation(
            BiConsumer<PlatformOutboundInstruction, StreamObserver<PlatformInboundInstruction>> handler) {
        return (i, s) -> {
            try {
                handler.accept(i, s);
                instructionAckSource.sendSuccessfulAck(i.getInstructionId(), s);
            } catch (Exception e) {
                logger.warn("Error happened while handling instruction {}.", i.getInstructionId());
                ErrorMessage instructionAckError = ErrorMessage
                        .newBuilder()
                        .setErrorCode(ErrorCode.INSTRUCTION_ACK_ERROR.errorCode())
                        .setLocation(axonServerConfiguration.getClientId())
                        .addDetails("Error happened while handling instruction")
                        .build();
                instructionAckSource.sendUnsuccessfulAck(i.getInstructionId(), instructionAckError, s);
            }
        };
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
     * Forces a disconnection from AxonServer for the specified context.
     *
     * @param context the (Bounded) Context for which the disconnection is required
     * @param cause   the cause of the disconnection
     */
    public void disconnectExceptionally(String context, Throwable cause) {
        if (isConnected(context)) {
            instructionStreams.get(context).onError(cause);
        }
    }

    /**
     * Returns {@code true} if a gRPC channel for the specific context is opened between client and AxonServer.
     * @param context the (Bounded) Context for for which is verified the AxonServer connection through the gRPC channel
     * @return if the gRPC channel is opened, false otherwise
     */
    public boolean isConnected(String context) {
        ManagedChannel channel = channels.get(context);
        return channel != null && !channel.isShutdown();
    }

    /**
     * Stops the Connection Manager, closing any active connections and preventing new connections from being created.
     * This shutdown operation is performed in the {@link Phase#EXTERNAL_CONNECTIONS} phase.
     */
    @ShutdownHandler(phase = Phase.EXTERNAL_CONNECTIONS)
    public void shutdown() {
        shutdown = true;
        instructionStreams.values().forEach(StreamObserver::onCompleted);
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
        ManagedChannel channel = channels.remove(context);
        if (channel != null) {
            shutdownChannel(channel, context);
        }
    }

    /**
     * Disconnects any active connections, forcing a new connection to be established when one is requested.
     */
    public void disconnect() {
        channels.forEach((context, channel) -> shutdownChannel(channel, context));
        channels.clear();
    }

    private void shutdownChannel(ManagedChannel channel, String context) {
        try {
            channel.shutdown();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Awaited Context [{}] comm-channel for 5 seconds. Will shutdown forcefully.", context);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Interrupted during shutdown of context [{}]", context);
        }
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
     * tied to it. The Axon Framework version resolver ({@link Supplier}<{@link String}>) is defaulted to
     * {@link AxonFrameworkVersionResolver}. The {@link AxonServerConfiguration} is a <b>hard requirements</b> and as
     * such should be provided.
     */
    public static class Builder {

        private static final int DEFAULT_POOL_SIZE = 1;

        private AxonServerConfiguration axonServerConfiguration;
        private Supplier<String> axonFrameworkVersionResolver = new AxonFrameworkVersionResolver();
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
        private Function<UpstreamAwareStreamObserver<PlatformOutboundInstruction>, StreamObserver<PlatformInboundInstruction>> requestStreamFactory = os -> (StreamObserver<PlatformInboundInstruction>) os
                .getRequestStream();
        private InstructionAckSource<PlatformInboundInstruction> instructionAckSource =
                new DefaultInstructionAckSource<>(ack -> PlatformInboundInstruction.newBuilder()
                                                                                   .setAck(ack)
                                                                                   .build());

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
         * Sets the Axon Framework version resolver used in order to communicate the client version to send to Axon
         * Server.
         *
         * @param axonFrameworkVersionResolver a string supplier that retrieve the current Axon Framework version
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder axonFrameworkVersionResolver(Supplier<String> axonFrameworkVersionResolver) {
            assertNonNull(axonFrameworkVersionResolver, "Axon Framework Version Resolver may not be null");
            this.axonFrameworkVersionResolver = axonFrameworkVersionResolver;
            return this;
        }

        /**
         * Sets the request stream factory that creates a request stream based on upstream.
         * Defaults to {@link UpstreamAwareStreamObserver#getRequestStream()}.
         *
         * @param requestStreamFactory factory that creates a request stream based on upstream
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder requestStreamFactory(
                Function<UpstreamAwareStreamObserver<PlatformOutboundInstruction>, StreamObserver<PlatformInboundInstruction>> requestStreamFactory) {
            assertNonNull(requestStreamFactory, "RequestStreamFactory may not be null");
            this.requestStreamFactory = requestStreamFactory;
            return this;
        }

        /**
         * Sets the instruction ack source used to send instruction acknowledgements.
         * Defaults to {@link DefaultInstructionAckSource}.
         *
         * @param instructionAckSource used to send instruction acknowledgements
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder instructionAckSource(InstructionAckSource<PlatformInboundInstruction> instructionAckSource) {
            assertNonNull(instructionAckSource, "InstructionAckSource may not be null");
            this.instructionAckSource = instructionAckSource;
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
