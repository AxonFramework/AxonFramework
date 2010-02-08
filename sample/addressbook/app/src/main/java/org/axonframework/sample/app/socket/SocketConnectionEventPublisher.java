/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.sample.app.socket;

import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.SequentialPolicy;
import org.axonframework.core.eventhandler.annotation.ConcurrentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Allard Buijze
 */
@ConcurrentEventListener(sequencingPolicyClass = SequentialPolicy.class)
public class SocketConnectionEventPublisher implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SocketConnectionEventPublisher.class);

    private ServerSocket serverSocket;
    private int port;
    private Thread acceptorThread;
    private volatile boolean isRunning = true;
    private EventBus eventBus;

    public void acceptConnection() throws IOException {
        serverSocket = new ServerSocket(port);
        acceptorThread = new Thread(this);
        acceptorThread.start();
        logger.info("Listing for socket listeners on port " + serverSocket.getLocalPort());
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void shutDown() throws IOException {
        isRunning = false;
        serverSocket.close();
        acceptorThread.interrupt();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                Socket socket = serverSocket.accept();
                // the listener will register itself
                new SocketPrintingEventListener(socket, eventBus);
            } catch (IOException e) {
                isRunning = false;
            }
        }
    }
}
