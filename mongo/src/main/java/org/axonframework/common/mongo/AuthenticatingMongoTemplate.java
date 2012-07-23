package org.axonframework.common.mongo;

import com.mongodb.DB;
import com.mongodb.Mongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract implementation for Mongo templates. Mongo templates give access to the collections in a Mongo Database used
 * by components of the Axon Framework. The AuthenticatingMongoTemplate takes care of the authentication against the
 * Mongo database.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AuthenticatingMongoTemplate {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticatingMongoTemplate.class);
    private static final String DEFAULT_AXONFRAMEWORK_DATABASE = "axonframework";

    private final String userName;
    private final char[] password;
    private final DB database;

    /**
     * Initializes the MongoTemplate to connect using the given <code>mongo</code> instance and a database with default
     * name "axonframework". The given <code>userName</code> and <code>password</code>, when not <code>null</code>, are
     * used to authenticate against the database.
     *
     * @param mongo    The Mongo instance configured to connect to the Mongo Server
     * @param userName The username to authenticate with. Use <code>null</code> to skip authentication
     * @param password The password to authenticate with. Use <code>null</code> to skip authentication
     */
    protected AuthenticatingMongoTemplate(Mongo mongo, String userName, char[] password) { //NOSONAR
        this(mongo, DEFAULT_AXONFRAMEWORK_DATABASE, userName, password);
    }

    /**
     * Initializes the MongoTemplate to connect using the given <code>mongo</code> instance and the database with given
     * <code>databaseName</code>. The given <code>userName</code> and <code>password</code>, when not
     * <code>null</code>, are used to authenticate against the database.
     *
     * @param mongo        The Mongo instance configured to connect to the Mongo Server
     * @param databaseName The name of the database containing the data
     * @param userName     The username to authenticate with. Use <code>null</code> to skip authentication
     * @param password     The password to authenticate with. Use <code>null</code> to skip authentication
     */
    protected AuthenticatingMongoTemplate(Mongo mongo, String databaseName, String userName, char[] password) {
        this.database = mongo.getDB(databaseName);
        this.userName = userName;
        this.password = password;
    }

    /**
     * Returns a reference to the Database with the configured database name. If a username and/or password have been
     * provided, these are used to authenticate against the database.
     * <p/>
     * Note that the configured <code>userName</code> and <code>password</code> are ignored if the database is already
     * in an authenticated state.
     *
     * @return a DB instance, referring to the database with configured name.
     *
     * @see com.mongodb.DB#isAuthenticated()
     * @see DB#authenticate(String, char[])
     */
    protected DB database() {
        if (!database.isAuthenticated() && (userName != null || password != null)) {
            if (!database.authenticate(userName, password)) {
                logger.warn("Failed to authenticate user '{}' against database. Incorrect credentials.", userName);
            }
        }
        return database;
    }
}