package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.source.GrpcHeartbeatSource;
import org.axonframework.config.Configuration;
import org.axonframework.config.ModuleConfiguration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.HEARTBEAT;

/**
 * Module configuration that defines the components needed to enable heartbeat and monitor the
 * availability of the connection with AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class HeartbeatConfiguration implements ModuleConfiguration {

    private final Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier;

    private final Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier;

    private final AtomicReference<Scheduler> heartbeatScheduler = new AtomicReference<>();

    /**
     * Default constructor for {@link HeartbeatConfiguration}, that uses {@link Configuration} in order to
     * retrieve the registered {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     */
    public HeartbeatConfiguration() {
        this(c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }

    /**
     * Creates a {@link HeartbeatConfiguration} using the provided functions to retrieve the
     * {@link AxonServerConnectionManager} and {@link AxonServerConfiguration}.
     *
     * @param connectionManagerSupplier       function to retrieve the {@link AxonServerConnectionManager}
     *                                        from {@link Configuration}
     * @param axonServerConfigurationSupplier function to retrieve the {@link AxonServerConfiguration}
     *                                        from {@link Configuration}
     */
    public HeartbeatConfiguration(
            Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier,
            Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier) {
        this.connectionManagerSupplier = connectionManagerSupplier;
        this.axonServerConfigurationSupplier = axonServerConfigurationSupplier;
    }

    /**
     * Initializes the {@link GrpcHeartbeatSource} component, needed to send heartbeats to AxonServer,
     * any time the client will receive an heartbeat from the server.
     * <p>
     * Initializes the {@link HeartbeatMonitor} component, needed to force a disconnection if the
     * communication between the client and the server is no longer available.
     * <p>
     * Initializes the {@link Scheduler} used to start and stop the monitor of the connection state.
     *
     * @param config the global configuration, providing access to generic components
     */
    @Override
    public void initialize(Configuration config) {
        AxonServerConnectionManager connectionManager = connectionManagerSupplier.apply(config);
        AxonServerConfiguration configuration = axonServerConfigurationSupplier.apply(config);
        String context = configuration.getContext();

        GrpcHeartbeatSource heartbeatSource = new GrpcHeartbeatSource(connectionManager, context);
        connectionManager.onOutboundInstruction(context, HEARTBEAT, i -> heartbeatSource.pulse());

        HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor(connectionManager, context);
        this.heartbeatScheduler.set(new Scheduler(heartbeatMonitor::run));
    }

    /**
     * Starts the monitoring of the connection state.
     * @throws IllegalStateException if the module is not initialized.
     */
    @Override
    public void start() {
        scheduler().start();
    }

    /**
     * Stops the monitoring of the connection state.
     * @throws IllegalStateException if the module is not initialized.
     */
    @Override
    public void shutdown() {
        scheduler().stop();
    }

    private Scheduler scheduler() {
        Supplier<RuntimeException> exceptionSupplier = () -> new IllegalStateException(
                "HeartbeatConfiguration not initialized.");
        return Optional.ofNullable(heartbeatScheduler.get()).orElseThrow(exceptionSupplier);
    }
}
