/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * @author Allard Buijze
 */
public class NioConnector {


    public static void main(String[] args) throws IOException, InterruptedException {
        Acceptor acceptor = new Acceptor();
        acceptor.start();

        acceptor.join();
    }

    private static class Acceptor extends Thread {

        private final ServerSocketChannel channel;
        private final Selector selector;

        private Acceptor() throws IOException {
            channel = ServerSocketChannel.open();
            channel.configureBlocking(false);

            channel.socket().bind(new InetSocketAddress(12345));
            selector = Selector.open();

            channel.register(selector, SelectionKey.OP_ACCEPT);
        }

        @Override
        public void run() {
            try {
                final ByteBuffer allocate = ByteBuffer.allocate(10);

                while (channel.isOpen()) {
                    int selected = selector.select();
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey nextKey = keys.next();
                        keys.remove();
                        if (nextKey.isAcceptable()) {
                            System.out.println("Incoming connection");
                            SocketChannel socketChannel = channel.accept();
                            socketChannel.configureBlocking(false);
                            socketChannel.write(ByteBuffer.wrap("Hello\n".getBytes()));
                            socketChannel.register(selector,
                                                   SelectionKey.OP_READ | SelectionKey.OP_CONNECT,
                                                   socketChannel);
                        }
                        if (nextKey.isReadable()) {
                            SocketChannel socket = (SocketChannel) nextKey.attachment();
                            int readSize = socket.read(allocate);
                            if (readSize == -1) {
                                System.out.println("Closing socket");
                                socket.close();

                            } else {
                                allocate.flip();
                                byte[] bytesToWrite = new byte[readSize];
                                allocate.get(bytesToWrite);
                                System.out.write(bytesToWrite);
                                allocate.compact();
                            }
                        }
                        else if (nextKey.isConnectable()) {
                            System.out.println("Connectable");
                            SocketChannel socket = (SocketChannel) nextKey.attachment();
                            socket.finishConnect();
                            if (!socket.isConnected()) {
                                System.out.println("Socket closed.");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
