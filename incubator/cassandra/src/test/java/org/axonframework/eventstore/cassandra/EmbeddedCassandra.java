package org.axonframework.eventstore.cassandra;

import org.apache.cassandra.cli.CliMain;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.service.EmbeddedCassandraService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.io.FileUtils.*;

/**
 * for testing only
 */
public class EmbeddedCassandra {
    public static final String HOSTS = "localhost:9160";
    public static final String CLUSTER_NAME = "EmbeddedTestCluster";
    public static final String KEYSPACE_NAME = "AxonKeySpace";
    public static final String CF_NAME = "Events";
    private String cassandraConfigDirPath = "/tmp/cassandra";
    private List<String> cassandraStartupCommands = new ArrayList<String>();

    private boolean cleanCassandra = true;
    private String hostname = "localhost";
    private int hostport = 9160;
    private File cassandraYaml = null;

    public static EmbeddedCassandraBuilder builder() {
        return new EmbeddedCassandraBuilder();
    }

    public static void start() {
        //embedded cassandra
        File cassandraYamlFile = new File(EmbeddedCassandra.class.getClassLoader().getResource("cassandra.yaml").getFile()) ;
        builder()
                .withCassandaConfigurationDirectoryPath("target/cassandra")
                .withCleanDataStore()
                .withHostname("localhost")
                .withHostport(9160)
                .withCassandaYamlFile(cassandraYamlFile)
                .build();
    }

    public static class EmbeddedCassandraBuilder {
        private final EmbeddedCassandra instance = new EmbeddedCassandra();

        public EmbeddedCassandraBuilder withCleanDataStore() {
            instance.setCleanCassandra(true);
            return this;
        }

        public EmbeddedCassandraBuilder withStartupCommands(List<String> cassandraCommands) {
            instance.setCassandraStartupCommands(cassandraCommands);
            return this;
        }

        public EmbeddedCassandraBuilder withHostname(String hostname) {
            instance.setHostname(hostname);
            return this;
        }

        public EmbeddedCassandraBuilder withHostport(int port) {
            instance.setHostport(port);
            return this;
        }

        public EmbeddedCassandraBuilder withCassandaConfigurationDirectoryPath(String path) {
            instance.setCassandraConfigDirPath(path);
            return this;
        }

        public EmbeddedCassandraBuilder withCassandaYamlFile(File cassandraYaml) {
            instance.setCassandraYamlFile(cassandraYaml);
            return this;
        }

        public EmbeddedCassandra build() {
            try {
                instance.init();
                return instance;
            }
            catch (IOException e) {
                throw new RuntimeException("Error building embedded Cassandra instance", e);
            }
        }
    }

    private EmbeddedCassandra() {
    }

    private void init() throws IOException {

        setupStorageConfigPath();

        if (cleanCassandra) {
            clean();
        }

        EmbeddedCassandraService cassandra = new EmbeddedCassandraService();
        cassandra.start();

        if (cassandraStartupCommands != null) {
            executeCommands();
        }
    }

    private void setupStorageConfigPath() throws IOException {
        if (cassandraConfigDirPath != null) {
            File configFile = new File(cassandraConfigDirPath);
            String configFileName = "file:" + configFile.getPath() + "/cassandra.yaml";
            System.setProperty("cassandra.config", configFileName);
        }
        else {
            throw new IOException("CassandraConfigDirPath is not configured, bailing.");
        }
    }

    private void setCassandraConfigDirPath(String cassandraConfigDirPath) {
        this.cassandraConfigDirPath = cassandraConfigDirPath;
    }

    private void setHostname(String hostname) {
        this.hostname = hostname;
    }

    private void setHostport(int hostport) {
        this.hostport = hostport;
    }

    private void setCassandraStartupCommands(List<String> cassandraCommands) {
        cassandraStartupCommands = cassandraCommands;
    }

    private void setCleanCassandra(Boolean cleanCassandra) {
        this.cleanCassandra = cleanCassandra;
    }

    private void setCassandraYamlFile(File cassandraYaml) {
        this.cassandraYaml = cassandraYaml;
    }

    private void executeCommands() {
        CliMain.connect(hostname, hostport);

        for (String command : cassandraStartupCommands) {
            try {
                CliMain.processStatement(command);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        CliMain.disconnect();
    }

    private void clean() throws IOException {
        cleanupDataDirectories();
        initializeCassandraYaml();
        makeDirsIfNotExist();
    }

    private void cleanupDataDirectories() throws IOException {
        deleteDirectory(new File(cassandraConfigDirPath));
    }

    private void initializeCassandraYaml() throws IOException {
        if (cassandraYaml != null) {
            copyFileToDirectory(cassandraYaml, new File(cassandraConfigDirPath));
        }
    }

    private void makeDirsIfNotExist() throws IOException {
        for (String s : Arrays.asList(DatabaseDescriptor.getAllDataFileLocations())) {
            forceMkdir(new File(s));
        }
        forceMkdir(new File(DatabaseDescriptor.getCommitLogLocation()));
    }
}
