/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.axonserver.connector.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
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
 * Utility class to work with Axon Server in tests.
 */
public class AxonServerUtils {

    public static void purgeEventsFromAxonServer(String hostname, int port) throws IOException {
        final URL url = URI.create(String.format("http://%s:%d/v1/public/purge-events?targetContext=%s", hostname, port,
                                                 URLEncoder.encode("default", StandardCharsets.UTF_8))).toURL();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("DELETE");
            connection.getInputStream().close();
            assertFalse(connection.getResponseCode() >= 300);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        waitForContextsCondition(hostname, port, contexts -> contexts.contains("default"));
    }

    public static List<String> contexts(String hostname, int port) throws IOException {
        final URL url = URI.create(String.format("http://%s:%d/v1/public/context", hostname, port)).toURL();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setRequestMethod("GET");

            assertEquals(200, connection.getResponseCode());

            return contexts(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ArrayList<String> contexts(HttpURLConnection connection) throws IOException {
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

    private static void waitForContextsCondition(String hostname, int port,
                                                 Predicate<List<String>> condition) {

        CountDownLatch latch = new CountDownLatch(1);
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
            scheduler.submit(() -> checkContextsCondition(hostname, port, condition, latch, scheduler))
                     .get();
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Condition on contexts has not been met!");
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkContextsCondition(String hostname, int port,
                                               Predicate<List<String>> condition,
                                               CountDownLatch latch, ScheduledExecutorService scheduler) {
        try {
            if (condition.test(contexts(hostname, port))) {
                latch.countDown();
            } else {
                scheduler.schedule(() -> checkContextsCondition(hostname, port, condition, latch, scheduler),
                                   10,
                                   TimeUnit.MILLISECONDS);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
