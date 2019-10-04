package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.grpc.GrpcHeartbeatSource;
import org.axonframework.config.Configuration;
import org.axonframework.config.ModuleConfiguration;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.HEARTBEAT;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatConfiguration implements ModuleConfiguration {

    private final Function<Configuration, AxonServerConnectionManager> connectionManagerSupplier;

    private final Function<Configuration, AxonServerConfiguration> axonServerConfigurationSupplier;

    private final AtomicReference<Scheduler> heartbeatScheduler = new AtomicReference<>();

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
        String context = configuration.getContext();

        GrpcHeartbeatSource heartbeatSource = new GrpcHeartbeatSource(connectionManager, context);
        connectionManager.onOutboundInstruction(context, HEARTBEAT, i -> heartbeatSource.send());

        HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor(connectionManager, context);
        this.heartbeatScheduler.set(new Scheduler(heartbeatMonitor::run));
    }

    @Override
    public void start() {
        Optional.ofNullable(heartbeatScheduler.get()).ifPresent(Scheduler::start);
    }

    @Override
    public void shutdown() {
        Optional.ofNullable(heartbeatScheduler.get()).ifPresent(Scheduler::stop);
    }
}
