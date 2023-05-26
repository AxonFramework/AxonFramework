package org.axonframework.utils;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import com.github.kagkarlsson.scheduler.task.Task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import javax.sql.DataSource;

/**
 * Utility methods for testing db-scheduler integration.
 *
 * @author Gerard Klijs
 */
public class DbSchedulerTestUtil {

    private DbSchedulerTestUtil() {
        //prevent instantiation
    }

    /**
     * Recreates the table used to store the tasks, this way we are both sure no old tasks are interfering, and the
     * table is properly created.
     *
     * @param dataSource a {@link DataSource} instance, which is used in the constructor of the {@link Scheduler}
     */
    public static void reCreateTable(DataSource dataSource) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement statement = connection.prepareStatement("drop table if exists scheduled_tasks;")) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "create table scheduled_tasks (\n"
                                     + "  task_name varchar(40) not null,\n"
                                     + "  task_instance varchar(40) not null,\n"
                                     + "  task_data blob,\n"
                                     + "  execution_time timestamp(6) not null,\n"
                                     + "  picked BOOLEAN not null,\n"
                                     + "  picked_by varchar(50),\n"
                                     + "  last_success timestamp(6) null,\n"
                                     + "  last_failure timestamp(6) null,\n"
                                     + "  consecutive_failures INT,\n"
                                     + "  last_heartbeat timestamp(6) null,\n"
                                     + "  version BIGINT not null,\n"
                                     + "  PRIMARY KEY (task_name, task_instance),\n"
                                     + ")")) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates and starts a scheduler
     *
     * @param dataSource a {@link DataSource} instance, which should have a {@code scheduled_tasks} table
     * @param task       the {@link Task} we want to test
     * @return a {@link Scheduler} that is started
     */
    public static Scheduler getAndStartScheduler(DataSource dataSource, Task<?> task) {
        Scheduler scheduler = new SchedulerBuilder(dataSource, Collections.singletonList(task))
                .threads(2)
                .pollingInterval(Duration.ofMillis(50L))
                .build();
        scheduler.start();
        return scheduler;
    }
}
