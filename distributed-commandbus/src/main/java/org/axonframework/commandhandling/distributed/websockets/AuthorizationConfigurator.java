package org.axonframework.commandhandling.distributed.websockets;

import javax.websocket.ClientEndpointConfig;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;

/**
 * A Configurator which enabled connecting to a Websocket server and authenticate using Basic authentication.
 *
 * @author Koen Lavooij
 */
public class AuthorizationConfigurator extends ClientEndpointConfig.Configurator {
    private final String username;
    private final String password;

    public AuthorizationConfigurator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        headers.put("Authorization", Collections.singletonList("Basic " + printBase64Binary((username + ":" + password).getBytes())));
    }
}
