/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.axonserver.connector.utils;

import org.axonframework.axonserver.connector.util.Scheduler;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Fake implementation of {@link Scheduler} used for testing purpose.
 *
 * @author Sara Pellegrini
 */
public class FakeScheduler implements Scheduler {

    private final NavigableSet<Execution> scheduledExecutions = new TreeSet<>();
    private final List<Execution> performedExecutions = new LinkedList<>();
    private FakeClock clock;

    public FakeScheduler() {
        Instant i = Instant.now();
        this.clock = new FakeClock(() -> i);
    }

    public FakeScheduler(FakeClock clock) {
        this.clock = clock;
    }

    public ScheduledTask schedule(Runnable command, Instant triggerTime) {
        Execution e = new Execution(command, triggerTime);
        scheduledExecutions.add(e);
        return mayInterruptIfRunning -> scheduledExecutions.remove(e);
    }

    @Override
    public ScheduledTask scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit timeUnit) {
        Instant first = clock.instant().plusMillis(timeUnit.toMillis(initialDelay));
        Runnable runnable = new Runnable() {
            private Instant next = first;

            @Override
            public void run() {
                command.run();
                next = next.plusMillis(timeUnit.toMillis(delay));
                schedule(this, next);
            }
        };
        return schedule(runnable, first);
    }

    @Override
    public void shutdownNow() {
        scheduledExecutions.clear();
    }

    /**
     * Moves time forward for given {@code delayInMillis} millis.
     *
     * @param delayInMillis for how long to move the time wheel in millis
     */
    public void timeElapses(long delayInMillis) {
        timeElapses(delayInMillis, MILLISECONDS);
    }

    /**
     * Moves time forward for given {@code delay} in given {@code timeUnit}.
     *
     * @param delay    for how long to move the time wheel in {@code timeUnit}
     * @param timeUnit time unit
     */
    public synchronized void timeElapses(long delay, TimeUnit timeUnit) {
        clock = clock.plusMillis(timeUnit.toMillis(delay));
        while (!scheduledExecutions.isEmpty() && !scheduledExecutions.first().instant.isAfter(clock.instant())) {
            Execution execution = scheduledExecutions.pollFirst();
            execution.command.run();
            performedExecutions.add(execution);
        }
    }

    public int performedExecutionsCount() {
        return performedExecutions.size();
    }

    private static class Execution implements Comparable<Execution> {

        private final Runnable command;
        private final Instant instant;

        public Execution(Runnable command, Instant instant) {
            this.command = command;
            this.instant = instant;
        }

        @Override
        public int compareTo(Execution o) {
            return this.instant.compareTo(o.instant);
        }
    }
}
