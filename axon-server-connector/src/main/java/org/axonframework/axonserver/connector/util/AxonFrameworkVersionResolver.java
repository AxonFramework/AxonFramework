package org.axonframework.axonserver.connector.util;


import org.axonframework.util.MavenArtifactVersionResolver;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Provides Axon Framework current version.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class AxonFrameworkVersionResolver implements Supplier<String> {

    private static final String ORG_AXONFRAMEWORK = "org.axonframework";
    private static final String AXON_SERVER_CONNECTOR = "axon-server-connector";
    private static AxonFrameworkVersionResolver instance;
    private final String version;

    private AxonFrameworkVersionResolver() {
        try {
            version = new MavenArtifactVersionResolver(ORG_AXONFRAMEWORK,
                                                       AXON_SERVER_CONNECTOR,
                                                       getClass().getClassLoader()).get();
        } catch (IOException e) {
            throw new RuntimeException("Impossible to read maven artifact version for " + AXON_SERVER_CONNECTOR);
        }
    }

    public static AxonFrameworkVersionResolver getInstance() {
        if (instance == null) {
            instance = new AxonFrameworkVersionResolver();
        }
        return instance;
    }


    @Override
    public String get() {
        return version;
    }
}
