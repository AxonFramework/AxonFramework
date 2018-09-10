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
        try {
            delegate.notifyInformation();
        } catch (Throwable t){
            //do nothing
        }
    }

    public void shutdown(){
        scheduler.shutdown();
    }



}
