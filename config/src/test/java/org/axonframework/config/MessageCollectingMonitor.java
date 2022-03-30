/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitorCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

public class MessageCollectingMonitor implements MessageMonitor<Message<?>> {
    private final List<Message<?>> messages = new ArrayList<>();
    private final CountDownLatch latch;

    public MessageCollectingMonitor() {
        latch = null;
    }

    public MessageCollectingMonitor(int count) {
        latch = new CountDownLatch(count);
    }

    @Override
    public MonitorCallback onMessageIngested(@Nonnull Message<?> message) {
        messages.add(message);
        if (latch != null) {
            latch.countDown();
        }
        return NoOpMessageMonitorCallback.INSTANCE;
    }

    public List<Message<?>> getMessages() {
        return messages;
    }

    public boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (latch == null) {
            return true;
        }
        return latch.await(timeout, timeUnit);
    }
}
