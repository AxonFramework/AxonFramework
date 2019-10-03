package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.grpc.GrpcHeartbeatSource;
import org.axonframework.config.Configuration;
import org.axonframework.config.ModuleConfiguration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatConfiguration implements ModuleConfiguration {

    private final Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier;

    private final Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier;

    private final AtomicReference<HeartbeatScheduler> heartbeatScheduler = new AtomicReference<>();

    public HeartbeatConfiguration() {
        this(c -> c.getComponent(AxonServerConnectionManager.class),
             c -> c.getComponent(AxonServerConfiguration.class));
    }

    public HeartbeatConfiguration(
            Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier,
            Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier) {
        this.connectionManagerSupplier = connectionManagerSupplier;
        this.axonServerConfigurationSupplier = axonServerConfigurationSupplier;
    }

    @Override
    public void initialize(Configuration config) {
        AxonServerConnectionManager connectionManager = connectionManagerSupplier.apply(config);
        AxonServerConfiguration configuration = axonServerConfigurationSupplier.apply(config);
        HeartbeatSource heartbeatSource = new GrpcHeartbeatSource(connectionManager, configuration.getContext());
        this.heartbeatScheduler.set(new HeartbeatScheduler(heartbeatSource));
    }

    @Override
    public void start() {
        Optional.ofNullable(heartbeatScheduler.get()).ifPresent(HeartbeatScheduler::start);
    }

    @Override
    public void shutdown() {
        Optional.ofNullable(heartbeatScheduler.get()).ifPresent(HeartbeatScheduler::stop);
    }
}
