/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandbus.distributed;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import javax.net.ServerSocketFactory;

/**
 * @author Allard Buijze
 */
public class TcpCommandBusConnector {

    private ServerSocket serverSocket;
    private ServerSocketFactory serverSocketFactory;
    private Executor executor;

    public TcpCommandBusConnector() {
    }

    public void connect() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (serverSocket == null) {
                        serverSocket = serverSocketFactory.createServerSocket();
                    }
                    final Socket connection = serverSocket.accept();
                    executor.execute(new ConnectionHandler(connection));
                } catch (IOException e) {
                    //
                }
            }
        });
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void setServerSocket(ServerSocketFactory serverSocketFactory) {
        this.serverSocketFactory = serverSocketFactory;
    }

    private static class ConnectionHandler implements Runnable {
        private final Socket connection;

        public ConnectionHandler(Socket connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            try {
                connection.getInputStream().read();
            } catch (IOException e) {
                // TODO: Implement
            }
        }
    }
}
