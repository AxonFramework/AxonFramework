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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class to work with Axon Server in tests.
 */
public class AxonServerUtils {

    /**
     * Calls the API of Axon Server at given {@code hostname} and (http) {@code port} to purge events of the given
     * {@code context}.
     *
     * @param hostname The hostname where AxonServer can be reached
     * @param port     The HTTP port AxonServer listens to for API calls
     * @param context  The context to purge
     * @throws IOException when an error occurs communicating with AxonServer
     */
    public static void purgeEventsFromAxonServer(String hostname, int port, String context) throws IOException {
        final URL url = URI.create(String.format("http://%s:%d/v1/public/purge-events?targetContext=%s", hostname, port,
                                                 URLEncoder.encode(context, StandardCharsets.UTF_8))).toURL();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("DELETE");
            connection.getInputStream().close();
            if (connection.getResponseCode() >= 300) {
                throw new IOException(
                        "Received unexpected response code from Axon Server: " + connection.getResponseCode());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
