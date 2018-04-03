package io.axoniq.axonhub.client.processor.schedule;

import io.axoniq.axonhub.client.processor.AxonHubEventProcessorInfoSource;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Sara Pellegrini on 15/03/2018.
 * sara.pellegrini@gmail.com
 */
public class ScheduledEventProcessorInfoSource implements AxonHubEventProcessorInfoSource {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final int initialDelay;

    private final int schedulingPeriod;

    private final AxonHubEventProcessorInfoSource delegate;

    public ScheduledEventProcessorInfoSource(
            int initialDelay,
            int schedulingPeriod,
            AxonHubEventProcessorInfoSource delegate) {
        this.initialDelay = initialDelay;
        this.schedulingPeriod = schedulingPeriod;
        this.delegate = delegate;
    }

    public void start(){
        scheduler.scheduleAtFixedRate(this::notifyInformation, initialDelay,schedulingPeriod, TimeUnit.MILLISECONDS);
    }

    public void notifyInformation(){
        delegate.notifyInformation();
    }

    public void shutdown(){
        scheduler.shutdown();
    }



}
