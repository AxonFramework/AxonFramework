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
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: marc
 */

public class AxonHubConfiguration {
    @Value("${axoniq.axonhub.servers}")
    private String routingServers;

    @Value("${axoniq.axonhub.clientName}")
    private String clientName;
    @Value("${axoniq.axonhub.componentName}")
    private String componentName;

    @Value("${axoniq.axonhub.token:#{null}}")
    private String token;
    @Value("${axoniq.axonhub.context:#{null}}")
    private String context;
    @Value("${axoniq.axonhub.ssl.certChainFile:#{null}}")
    private String certFile;
    @Value("${axoniq.axonhub.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${axoniq.axonhub.flowControl.initialNrOfPermits:100000}")
    private Integer initialNrOfPermits;
    @Value("${axoniq.axonhub.flowControl.nrOfNewPermits:90000}")
    private Integer nrOfNewPermits;
    @Value("${axoniq.axonhub.flowControl.newPermitsThreshold:10000}")
    private Integer newPermitsThreshold;
    private EventCipher eventCipher = new EventCipher();


    public AxonHubConfiguration() {
    }

    private AxonHubConfiguration(String routingServers, String clientName, String componentName) {
        this.routingServers = routingServers;
        this.clientName = clientName;
        this.componentName = componentName;
    }


    public String getRoutingServers() {
        return routingServers;
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

    public void setRoutingServers(String routingServers) {
        this.routingServers = routingServers;
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
        String[] serverArr = routingServers.split(",");
        return Arrays.stream(serverArr).map(server -> {
            String[] s = server.trim().split(":");
            return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(Integer.valueOf(s[1])).build();
        }).collect(Collectors.toList());
    }

    public EventCipher getEventCipher() {
        return eventCipher;
    }

    @Value("${axoniq.axonhub.eventSecretKey:#{null}}")
    private void setEventSecretKey(String key) {
        if(key != null && key.length() > 0) {
            eventCipher = new EventCipher(key.getBytes(StandardCharsets.US_ASCII));
        }
    }


    @SuppressWarnings("unused")
    public static class Builder {
        private AxonHubConfiguration instance;
        public Builder(String servers, String componentName, String clientName) {
            instance = new AxonHubConfiguration(servers, clientName, componentName);
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

    public static Builder newBuilder(String servers, String componentName, String clientName) {
        return new Builder( servers, componentName, clientName);
    }
}
