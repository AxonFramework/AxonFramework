package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author Allard Buijze
 */
public class RabbitMQBenchmark {

    private static final int THREAD_COUNT = 10;
    private static final int COMMIT_SIZE = 10;
    private static final int COMMIT_COUNT = 1500;

    public static void main(String[] args) throws IOException, InterruptedException {
        final Connection connection = new ConnectionFactory().newConnection();
        final Channel channel = connection.createChannel();
        String queueName = channel.queueDeclare().getQueue();
        execute("Transactional and Channel pooling", createChannelPoolSharingThreads(connection, queueName));
        queueName = refreshQueue(channel, queueName);
        execute("Transactional, new Channel per tx", createChannelCreatingThreads(connection, queueName, true));
        queueName = refreshQueue(channel, queueName);
        execute("Non-transactional, new Channel per tx", createChannelCreatingThreads(connection, queueName, false));
        queueName = refreshQueue(channel, queueName);
        execute("Non-transactional, single Channel", createChannelSharingThreads(connection, queueName));
        channel.confirmSelect();
        connection.close();
    }

    private static List<Thread> createChannelCreatingThreads(final Connection connection, final String queueName,
                                                             final boolean transactional) {
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int t = 0; t < COMMIT_COUNT; t++) {
                            final Channel localChannel = connection.createChannel();
                            if (transactional) {
                                localChannel.txSelect();
                            }
                            for (int j = 0; j < COMMIT_SIZE; j++) {
                                localChannel.basicPublish("", queueName, null, ("message" + t).getBytes("UTF-8"));
                            }
                            if (transactional) {
                                localChannel.txCommit();
                            }
                            localChannel.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }
        return threads;
    }

    private static List<Thread> createChannelSharingThreads(final Connection connection, final String queueName)
            throws IOException {
        List<Thread> threads = new ArrayList<Thread>();
        final Channel localChannel = connection.createChannel();
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int t = 0; t < COMMIT_COUNT; t++) {
                            for (int j = 0; j < COMMIT_SIZE; j++) {
                                localChannel.basicPublish("", queueName, null, ("message" + t).getBytes("UTF-8"));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }
        return threads;
    }

    private static List<Thread> createChannelPoolSharingThreads(final Connection connection, final String queueName) {
        List<Thread> threads = new ArrayList<Thread>();
        final Queue<Channel> channels = new ArrayBlockingQueue<Channel>(15);
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int t = 0; t < COMMIT_COUNT; t++) {
                            Channel localChannel = channels.poll();
                            if (localChannel == null) {
                                localChannel = connection.createChannel();
                            }
                            localChannel.txSelect();
                            for (int j = 0; j < COMMIT_SIZE; j++) {
                                localChannel.basicPublish("", queueName, null, ("message" + t).getBytes("UTF-8"));
                            }
                            localChannel.txCommit();
                            if (!channels.offer(localChannel)) {
                                localChannel.close();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }
        return threads;
    }

    private static void execute(String description, List<Thread> threads) throws InterruptedException {
        long start = System.currentTimeMillis();
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        long end = System.currentTimeMillis();
        System.out.println(description + ". Dispatching took " + (end - start) + " millis");
    }

    private static String refreshQueue(Channel channel, String queueName) throws IOException {
        channel.queueDelete(queueName);
        queueName = channel.queueDeclare().getQueue();
        return queueName;
    }
}
