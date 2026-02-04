/*
 * Copyright (c) 2010-2025. Axon Framework
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class for the {@link AxonServerContainer}, used to initialize the cluster.
 *
 * @author Milan Savic
 * @author Sara Pelligrini
 * @since 4.8.0
 */
public class AxonServerContainerUtils {

    /**
     * Constant {@code boolean} specifying that a context supports DCB.
     */
    public static final boolean DCB_CONTEXT = true;
    /**
     * Constant {@code boolean} specifying that a context does <b>not</b> support DCB.
     */
    public static final boolean NO_DCB_CONTEXT = false;

    /**
     * Initialize the cluster of the Axon Server instance located at the given {@code hostname} and {@code port}
     * combination.
     * <p>
     * Note that this constructs the contexts {@code _admin} and {@code default}.
     *
     * @param hostname       The hostname of the Axon Server instance to initiate the cluster for.
     * @param port           The port of the Axon Server instance to initiate the cluster for.
     * @param shouldBeReused If set to {@code true}, ensure the cluster is not accidentally initialized twice.
     * @param dcbContext A {@code boolean} stating whether a DCB or non-DCB context is being created.
     * @throws IOException When there are issues with the HTTP connection to the Axon Server instance at the given
     *                     {@code hostname} and {@code port}.
     */
    public static void initCluster(String hostname, int port, boolean shouldBeReused, boolean dcbContext) throws IOException {
        if (shouldBeReused && initialized(hostname, port)) {
            return;
        }
        final URL url = URI.create(String.format("http://%s:%d/v2/cluster/init?dcb=%s", hostname, port, dcbContext)).toURL();
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
    public static List<String> contexts(String hostname, int port) throws IOException {
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

    /**
     * Retrieves all the internal contexts (registered in the RAFT protocol) of the Axon Server instance located at the
     * given {@code hostname} and {@code port} combination.
     *
     * @param hostname The hostname of the Axon Server instance to create the given {@code context} of.
     * @param port     The port of the Axon Server instance to create the given {@code context} of.
     * @return All the contexts of the Axon Server instances located at the given {@code hostname} and {@code port}
     * combination.
     * @throws IOException When there are issues with the HTTP connection to the Axon Server instance at the given
     *                     {@code hostname} and {@code port}.
     */
    public static List<String> internalContexts(String hostname, int port) throws IOException {
        final URL url = new URL(String.format("http://%s:%d/internal/raft/contexts", hostname, port));
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
            if (condition.test(internalContexts(hostname, port))) {
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

    private static boolean initialized(String hostname, int port) throws IOException {
        try {
            List<String> cont = internalContexts(hostname, port);
            return cont.contains("_admin") && cont.contains("default");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Calls the API of Axon Server at given {@code hostname} and (http) {@code port} to purge events of the given
     * {@code context}.
     *
     * @param hostname   The hostname where AxonServer can be reached.
     * @param port       The HTTP port AxonServer listens to for API calls.
     * @param context    The context to purge.
     * @param dcbContext A {@code boolean} stating whether a DCB or non-DCB context is being purged.
     * @throws IOException When an error occurs communicating with Axon Server.
     * @since 5.0.0
     */
    public static void purgeEventsFromAxonServer(String hostname,
                                                 int port,
                                                 String context,
                                                 boolean dcbContext) throws IOException {
        deleteContext(hostname, port, context);
        createContext(hostname, port, context, dcbContext);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calls the API of Axon Server at the given {@code hostname} and (http) {@code port} to delete the given
     * {@code context}.
     *
     * @param hostname The hostname where Axon Server can be reached.
     * @param port     The HTTP port Axon Server listens to for API calls.
     * @param context  The context to delete.
     * @throws IOException When an error occurs communicating with Axon Server.
     */
    public static void deleteContext(String hostname, int port, String context) throws IOException {
        URL url = URI.create(String.format("http://%s:%d/v1/context/%s", hostname, port, context)).toURL();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("DELETE");
            connection.getInputStream().close();
            assertEquals(202, connection.getResponseCode());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        waitForContextsCondition(hostname, port, contexts -> !contexts.contains(context));
    }

    /**
     * Calls the API of Axon Server at the given {@code hostname} and (http) {@code port} to create a context with the
     * given {@code context} name. The {@code dcbContext} dictates whether the context to be created support DCB, yes or
     * no.
     *
     * @param hostname   The hostname where Axon Server can be reached.
     * @param port       The HTTP port Axon Server listens to for API calls.
     * @param context    The context to create.
     * @param dcbContext A {@code boolean} stating whether a DCB or non-DCB context is being created.
     * @throws IOException When an error occurs communicating with Axon Server.
     */
    public static void createContext(String hostname, int port, String context, boolean dcbContext) throws IOException {
        URL url = URI.create(String.format("http://%s:%d/v1/context", hostname, port)).toURL();
        HttpURLConnection connection = null;
        try {
            String jsonRequest = String.format(
                    "{\"context\": \"%s\", \"dcbContext\": %b, \"replicationGroup\": \"%s\", \"roles\": [{ \"node\": \"axonserver\", \"role\": \"PRIMARY\" }]}",
                    context,
                    dcbContext,
                    context
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonRequest.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            connection.getInputStream().close();
            assertEquals(202, connection.getResponseCode());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        waitForContextsCondition(hostname, port, contexts -> contexts.contains(context));
    }
}
