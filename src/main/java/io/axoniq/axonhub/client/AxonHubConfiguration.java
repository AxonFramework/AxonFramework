/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client;

import io.axoniq.axonhub.client.event.util.EventCipher;
import io.axoniq.platform.grpc.NodeInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: marc
 */
@ConfigurationProperties(prefix = "axoniq.axonhub")
public class AxonHubConfiguration {
    private static final int DEFAULT_GRPC_PORT = 8124;
    private String servers;
    private String clientName = ManagementFactory.getRuntimeMXBean().getName();
    private String componentName;

    private String token;
    private String context;
    private String certFile;
    private boolean sslEnabled;

    private Integer initialNrOfPermits = 1000000;
    private Integer nrOfNewPermits = 90000;
    private Integer newPermitsThreshold = 100000;

    private int commandThreads = 10;
    private int queryThreads = 10;

    private int processorsNotificationRate = 5000;

    private int processorsNotificationInitialDelay = 5000;

    private EventCipher eventCipher = new EventCipher();
    private long heartbeatTimeout = 5000;

    private long checkAliveDelay = 1000;
    private long checkAliveInterval = 1000;




    public AxonHubConfiguration() {
    }

    private AxonHubConfiguration(String routingServers,  String componentName) {
        this.servers = routingServers;
        this.componentName = componentName;
    }


    public String getServers() {
        return servers;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public void setServers(String routingServers) {
        this.servers = routingServers;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCertFile() {
        return certFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Integer getInitialNrOfPermits() {
        return initialNrOfPermits;
    }

    public void setInitialNrOfPermits(Integer initialNrOfPermits) {
        this.initialNrOfPermits = initialNrOfPermits;
    }

    public Integer getNrOfNewPermits() {
        return nrOfNewPermits;
    }

    public void setNrOfNewPermits(Integer nrOfNewPermits) {
        this.nrOfNewPermits = nrOfNewPermits;
    }

    public Integer getNewPermitsThreshold() {
        return newPermitsThreshold;
    }

    public void setNewPermitsThreshold(Integer newPermitsThreshold) {
        this.newPermitsThreshold = newPermitsThreshold;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public List<NodeInfo> routingServers() {
        String[] serverArr = servers.split(",");
        return Arrays.stream(serverArr).map(server -> {
            String[] s = server.trim().split(":");
            if( s.length > 1) {
                return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(Integer.valueOf(s[1])).build();
            }
            return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(DEFAULT_GRPC_PORT).build();
        }).collect(Collectors.toList());
    }

    public EventCipher getEventCipher() {
        return eventCipher;
    }

    private void setEventSecretKey(String key) {
        if(key != null && key.length() > 0) {
            eventCipher = new EventCipher(key.getBytes(StandardCharsets.US_ASCII));
        }
    }

    public Integer getCommandThreads() {
        return commandThreads;
    }

    public void setCommandThreads(Integer commandThreads) {
        this.commandThreads = commandThreads;
    }

    public int getQueryThreads() {
        return queryThreads;
    }

    public void setQueryThreads(int queryThreads) {
        this.queryThreads = queryThreads;
    }

    public int getProcessorsNotificationRate() {
        return processorsNotificationRate;
    }

    public void setProcessorsNotificationRate(int processorsNotificationRate) {
        this.processorsNotificationRate = processorsNotificationRate;
    }

    public int getProcessorsNotificationInitialDelay() {
        return processorsNotificationInitialDelay;
    }

    public void setProcessorsNotificationInitialDelay(int processorsNotificationInitialDelay) {
        this.processorsNotificationInitialDelay = processorsNotificationInitialDelay;
    }

    public long getHeartbeatTimeout() {
        return this.heartbeatTimeout;
    }

    public void setHeartbeatTimeout(long heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public void setCommandThreads(int commandThreads) {
        this.commandThreads = commandThreads;
    }

    public long getCheckAliveDelay() {
        return checkAliveDelay;
    }

    public void setCheckAliveDelay(long checkAliveDelay) {
        this.checkAliveDelay = checkAliveDelay;
    }

    public long getCheckAliveInterval() {
        return checkAliveInterval;
    }

    public void setCheckAliveInterval(long checkAliveInterval) {
        this.checkAliveInterval = checkAliveInterval;
    }

    @SuppressWarnings("unused")
    public static class Builder {
        private AxonHubConfiguration instance;
        public Builder(String servers, String componentName) {
            instance = new AxonHubConfiguration(servers, componentName);
            instance.initialNrOfPermits = 100000;
            instance.nrOfNewPermits = 90000;
            instance.newPermitsThreshold = 10000;
        }

        public Builder ssl(String certFile) {
            instance.certFile = certFile;
            instance.sslEnabled = true;
            return this;
        }

        public Builder token(String token) {
            instance.token = token;
            return this;
        }

        public Builder context(String context) {
            instance.context = context;
            return this;
        }

        public Builder flowControl(int initialNrOfPermits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.initialNrOfPermits = initialNrOfPermits;
            instance.nrOfNewPermits = nrOfNewPermits;
            instance.newPermitsThreshold = newPermitsThreshold;
            return this;
        }

        public Builder setEventSecretKey(String key) {
            instance.setEventSecretKey(key);
            return this;
        }

        public Builder eventCipher(EventCipher eventCipher) {
            instance.eventCipher = eventCipher;
            return this;
        }

        public AxonHubConfiguration build() {
            return instance;
        }
    }

    public static Builder newBuilder(String servers, String componentName) {
        return new Builder( servers, componentName);
    }
}
