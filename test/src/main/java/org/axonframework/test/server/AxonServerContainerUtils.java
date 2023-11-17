/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.test.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.axonframework.common.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Utility class for the {@link AxonServerContainer}, used to initialize the cluster.
 *
 * @author Milan Savic
 * @author Sara Pelligrini
 * @since 4.8.0
 */
class AxonServerContainerUtils {

    /**
     * Initialize the cluster of the Axon Server instance located at the given {@code hostname} and {@code port}
     * combination.
     * <p>
     * Note that this constructs the contexts {@code _admin} and {@code default}.
     *
     * @param hostname The hostname of the Axon Server instance to initiate the cluster for.
     * @param port     The port of the Axon Server instance to initiate the cluster for.
     * @throws IOException When there are issues with the HTTP connection to the Axon Server instance at the given
     *                     {@code hostname} and {@code port}.
     */
    static void initCluster(String hostname, int port) throws IOException {
        final URL url = URI.create(String.format("http://%s:%d/v1/context/init", hostname, port)).toURL();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.getInputStream().close();

            int responseCode = connection.getResponseCode();
            Assert.isTrue(202 == responseCode, () -> "The response code [" + responseCode + "] did not match 202.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        waitForContextsCondition(
                hostname, port,
                contexts -> contexts.contains("_admin") && contexts.contains("default")
        );
    }

    /**
     * Retrieves all the contexts of the Axon Server instance located at the given {@code hostname} and {@code port}
     * combination.
     *
     * @param hostname The hostname of the Axon Server instance to create the given {@code context} of.
     * @param port     The port of the Axon Server instance to create the given {@code context} of.
     * @return All the contexts of the Axon Server instances located at the given {@code hostname} and {@code port}
     * combination.
     * @throws IOException When there are issues with the HTTP connection to the Axon Server instance at the given
     *                     {@code hostname} and {@code port}.
     */
    static List<String> contexts(String hostname, int port) throws IOException {
        final URL url = URI.create(String.format("http://%s:%d/v1/public/context", hostname, port)).toURL();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            Assert.isTrue(200 == responseCode, () -> "The response code [" + responseCode + "] did not match 200.");

            return contexts(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<String> contexts(HttpURLConnection connection) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder output = new StringBuilder();
        String outputLine;
        while ((outputLine = br.readLine()) != null) {
            output.append(outputLine);
        }
        JsonElement jsonElement = JsonParser.parseString(output.toString());
        ArrayList<String> contexts = new ArrayList<>();
        for (JsonElement element : jsonElement.getAsJsonArray()) {
            String context = element.getAsJsonObject()
                                    .get("context")
                                    .getAsString();
            contexts.add(context);
        }
        return contexts;
    }

    private static void waitForContextsCondition(String hostname,
                                                 int port,
                                                 Predicate<List<String>> condition) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            scheduler.submit(() -> checkContextsCondition(hostname, port, condition, latch, scheduler))
                     .get();
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Condition on contexts has not been met!");
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            scheduler.shutdown();
        }
    }

    private static void checkContextsCondition(String hostname, int port,
                                               Predicate<List<String>> condition,
                                               CountDownLatch latch, ScheduledExecutorService scheduler) {
        try {
            if (condition.test(contexts(hostname, port))) {
                latch.countDown();
            } else {
                scheduler.schedule(
                        () -> checkContextsCondition(hostname, port, condition, latch, scheduler),
                        10, TimeUnit.MILLISECONDS
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
