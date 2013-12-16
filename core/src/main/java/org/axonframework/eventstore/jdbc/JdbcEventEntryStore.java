/*
 * Copyright (c) 2010-2013. Axon Framework
 *
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



package org.axonframework.eventstore.jdbc;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;
import org.joda.time.DateTime;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of the EventEntryStore that stores events in DomainEventEntry table and snapshot events in
 * SnapshotEventEntry table.
 * <p/>
 * @author Allard Buijze
 * @author Kristian Rosenvold
 *
 * @since 2.1
 */
public class JdbcEventEntryStore implements EventEntryStore {

	private static final String unprefixedFields = "eventIdentifier, aggregateIdentifier, sequenceNumber, timeStamp, payloadType, payloadRevision, payload, metaData";
    private static final String stdFields = "e.eventIdentifier, e.aggregateIdentifier, e.sequenceNumber, e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData";

	private final DataSource dataSource;

	public JdbcEventEntryStore(DataSource dataSource) {
		this.dataSource = dataSource;
	}


	private String getInsertSql(final String tableName) {
		return "insert into " + tableName + " (" + unprefixedFields +",type) values (?,?,?,?,?,?,?,?,?)";
	}


	@Override
	@SuppressWarnings({"unchecked"})
	public SimpleSerializedDomainEventData loadLastSnapshotEvent(String aggregateType, Object identifier) {
		// Maybe do select top(1)
		PreparedStatement preparedStatement =
				prepareStatement("select " + stdFields + " from SnapshotEventEntry e " +
						"WHERE e.aggregateIdentifier = ? AND e.type = ? ORDER BY e.sequenceNumber DESC");
		ResultSet rs;
		try {
			preparedStatement.setString(1, identifier.toString());
			preparedStatement.setString(2, aggregateType);
			rs = preparedStatement.executeQuery();
			if (rs.next()) {
				return createSimpleSerializedDomainEventData(rs);
			}
			return null;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
				closeAllQuietly(preparedStatement);
		}
	}

	private static SimpleSerializedDomainEventData createSimpleSerializedDomainEventData(ResultSet rs)  {
		try {
			return new SimpleSerializedDomainEventData(rs.getString(1), rs.getString(2),
					rs.getLong(3), rs.getTimestamp(4), rs.getString(5), rs.getString(6),
					rs.getBytes(7), rs.getBytes(8));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}


	@Override
	@SuppressWarnings({"unchecked"})
	public Iterator<SerializedDomainEventData> fetchFiltered(String whereClause, List<Object> parameters,
			int batchSize) {
		ResultSet resultSet = fetchAll(batchSize, whereClause, parameters);
		return new BatchingIterator(resultSet);
	}


	private String getWhereClause(String whereClause) {
		return whereClause != null ? "WHERE " + whereClause : "";
	}

	@Override
	public void persistSnapshot(String aggregateType, DomainEventMessage snapshotEvent,
			SerializedObject serializedPayload, SerializedObject serializedMetaData) {
		doInsert(aggregateType, snapshotEvent, serializedPayload, serializedMetaData, "SnapshotEventEntry");
	}

	@Override
	@SuppressWarnings({"unchecked"})
	public void persistEvent(String aggregateType, DomainEventMessage event, SerializedObject serializedPayload,
			SerializedObject serializedMetaData) {
		doInsert(aggregateType, event, serializedPayload, serializedMetaData, "DomainEventEntry");
	}

	private void doInsert(String aggregateType, DomainEventMessage snapshotEvent, SerializedObject serializedPayload,
			SerializedObject serializedMetaData, String snapshotEventEntry) {
		String insert = getInsertSql(snapshotEventEntry);

		PreparedStatement preparedStatement = prepareStatement(insert);
		try {
			preparedStatement.setString(1, snapshotEvent.getIdentifier());
			preparedStatement.setString(2, snapshotEvent.getAggregateIdentifier().toString());
			preparedStatement.setLong(3, snapshotEvent.getSequenceNumber());
			preparedStatement.setTimestamp(4, new Timestamp(snapshotEvent.getTimestamp().getMillis()));
			preparedStatement.setString(5, serializedPayload.getType().getName());
			preparedStatement.setString(6, serializedPayload.getType().getRevision());
            //noinspection unchecked
            preparedStatement.setBytes(7, ((SerializedObject<byte[]>) serializedPayload).getData());
			byte[] data = (byte[]) serializedMetaData.getData();
			preparedStatement.setBytes(8, Arrays.copyOf(data, data.length));
			preparedStatement.setString(9, aggregateType);

			preparedStatement.executeUpdate();
            preparedStatement.getConnection().commit();
			closeAllQuietly(preparedStatement);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void pruneSnapshots(String type, DomainEventMessage mostRecentSnapshotEvent, int maxSnapshotsArchived) {
		Iterator<Long> redundantSnapshots = findRedundantSnapshots(type, mostRecentSnapshotEvent,
				maxSnapshotsArchived);
		if (redundantSnapshots.hasNext()) {
			Long sequenceOfFirstSnapshotToPrune = redundantSnapshots.next();
			try {
				String sql = "DELETE FROM SnapshotEventEntry e "
						+ "WHERE e.type = ? "
						+ "AND e.aggregateIdentifier = ? "
						+ "AND e.sequenceNumber <= ?";
				PreparedStatement preparedStatement = prepareStatement(sql);
				preparedStatement.setString(1, type);
				preparedStatement.setString(2, mostRecentSnapshotEvent.getAggregateIdentifier().toString());
				preparedStatement.setLong(3, sequenceOfFirstSnapshotToPrune);
				preparedStatement.executeUpdate();
                preparedStatement.getConnection().commit();
				closeAllQuietly(preparedStatement);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Finds the first of redundant snapshots, returned as an iterator for convenience purposes.
	 *
	 *
	 * @param type                 the type of the aggregate for which to find redundant snapshots
	 * @param snapshotEvent        the last appended snapshot event
	 * @param maxSnapshotsArchived the number of snapshots that may remain archived
	 * @return an iterator over the snapshots found
	 */
	@SuppressWarnings({"unchecked"})
	private Iterator<Long> findRedundantSnapshots(String type, DomainEventMessage snapshotEvent,
			int maxSnapshotsArchived) {
		String sql = "SELECT e.sequenceNumber FROM SnapshotEventEntry e "
				+ "WHERE e.type = ? AND e.aggregateIdentifier = ? "
				+ "ORDER BY e.sequenceNumber DESC";
		PreparedStatement preparedStatement = prepareStatement(sql);
		try {
			preparedStatement.setString(1, type);
			preparedStatement.setString(2, snapshotEvent.getAggregateIdentifier().toString());
			ResultSet resultSet = preparedStatement.executeQuery();
            //noinspection StatementWithEmptyBody
            while (maxSnapshotsArchived-- > 0 && resultSet.next())
			{
				// ignore
			}
			List<Long> result = new ArrayList<Long>();
			while (resultSet.next()){
				result.add( resultSet.getLong(1));
			}
			closeAllQuietly( preparedStatement);
			return result.iterator();

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings({"unchecked"})
	@Override
	public Iterator<SerializedDomainEventData> fetchAggregateStream(String aggregateType, Object identifier,
			long firstSequenceNumber,
			int batchSize) {

		ResultSet resultSet = fetchFromSequenceNumber(firstSequenceNumber, identifier, aggregateType, batchSize);
		return new BatchingIterator(resultSet);
	}

	@SuppressWarnings("unchecked")
	private ResultSet fetchFromSequenceNumber(long firstSequenceNumber, Object id, String typeId, int batchSize) {
		try {
			String sql = "select " + stdFields + " from DomainEventEntry e "
					+ "WHERE e.aggregateIdentifier = ? AND type = ? "
					+ "AND e.sequenceNumber >= ? "
					+ "ORDER BY e.sequenceNumber ASC";
			PreparedStatement ps = getPreparedStatement(batchSize, dataSource, sql);
			ps.setString(1, id.toString());
			ps.setString(2, typeId);
			ps.setLong(3, firstSequenceNumber);
			return ps.executeQuery();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private ResultSet fetchAll(int batchSize, String whereClause, List<Object> parameters) {
		try {
			final String sql1 = "select " + stdFields + " from DomainEventEntry e " + getWhereClause(whereClause) +
					" ORDER BY e.timeStamp ASC, e.sequenceNumber ASC, e.aggregateIdentifier ASC ";
			PreparedStatement preparedStatement = getPreparedStatement(batchSize, dataSource, sql1);
			int startWhere = 1;
			for (int i = 0; i < parameters.size(); i++) {
				Object x = parameters.get(i);
				if (x instanceof DateTime) x = new Timestamp(((DateTime) x).getMillis());
				preparedStatement.setObject(startWhere + i, x);
			}
			return preparedStatement.executeQuery();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private PreparedStatement getPreparedStatement(int batchSize, DataSource dataSource,
			String sql1) throws SQLException {
		Connection con = dataSource.getConnection();
		con.setAutoCommit(false);
		PreparedStatement preparedStatement = con.prepareStatement(sql1);
		preparedStatement.setFetchSize(batchSize);
		return preparedStatement;
	}


	private static class BatchingIterator implements Iterator<SerializedDomainEventData>, Closeable {
		private final ResultSet rs;
		boolean hasCalledNext = false;
		boolean hasNext;

		public BatchingIterator(ResultSet resultSet) {
			this.rs = resultSet;
		}

		@Override
		public boolean hasNext() {
			try {
				establishNext();
				return hasNext;
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		private void establishNext() throws SQLException {
			if (!hasCalledNext) {
				hasNext = rs.next();
				hasCalledNext = true;
			}
		}

		@Override
		public SerializedDomainEventData next() {
			try {
				establishNext();
				return createSimpleSerializedDomainEventData(rs);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			} finally {
				hasCalledNext = false;
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Not implemented");
		}

		@Override
		public void close() throws IOException {
			try {
				Statement statement = rs.getStatement();
				statement.getConnection().setAutoCommit(true);
				closeAllQuietly(statement);
			} catch (SQLException e) {
				throw new IOException(e);
			}
		}
	}

	private PreparedStatement prepareStatement(String insert) {
		try {
			return getConnection().prepareStatement(insert);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}


	private Connection getConnection() throws RuntimeException {

		try {
			return dataSource.getConnection();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private static void closeAllQuietly(Statement statement){
		if (statement != null)  {
			try {
				closeQuietly(statement.getConnection());
				statement.close();
			} catch (SQLException ignore) {
			}
		}
	}

	private static void closeQuietly(Connection connection){
		if (connection != null)  {
			try {
				connection.close();
			} catch (SQLException ignore) {
			}
		}
	}

    public void createSchema() throws SQLException {
        final Connection connection = dataSource.getConnection();
        final Statement statement = connection.createStatement();
        statement.execute(databaseSchema);
        connection.commit();
        closeAllQuietly(statement);
    }

    public void deleteAllEventData() throws SQLException {
        final Connection conn = dataSource.getConnection();
        final PreparedStatement preparedStatement = conn.prepareStatement("DELETE FROM DomainEventEntry");
        preparedStatement.executeUpdate();
        final PreparedStatement preparedStatement1 = conn.prepareStatement("DELETE FROM SnapshotEventEntry");
        preparedStatement1.executeUpdate();
        closeAllQuietly(preparedStatement);
        closeAllQuietly( preparedStatement1);
    }

	static String databaseSchema = "create table AssociationValueEntry (\n" +
			"        id bigint not null,\n" +
			"        associationKey varchar(255),\n" +
			"        associationValue varchar(255),\n" +
			"        sagaId varchar(255),\n" +
			"        sagaType varchar(255),\n" +
			"        primary key (id)\n" +
			"    );\n" +
			"\n" +
			"create table DomainEventEntry (\n" +
			"        aggregateIdentifier varchar(255) not null,\n" +
			"        sequenceNumber bigint not null,\n" +
			"        type varchar(255) not null,\n" +
			"        eventIdentifier varchar(255) not null,\n" +
			"        metaData blob,\n" +
			"        payload blob not null,\n" +
			"        payloadRevision varchar(255),\n" +
			"        payloadType varchar(255) not null,\n" +
			"        timeStamp varchar(255) not null,\n" +
			"        primary key (aggregateIdentifier, sequenceNumber, type)\n" +
			"    );\n" +
			"\n" +
			"    create table SagaEntry (\n" +
			"        sagaId varchar(255) not null,\n" +
			"        revision varchar(255),\n" +
			"        sagaType varchar(255),\n" +
			"        serializedSaga blob,\n" +
			"        primary key (sagaId)\n" +
			"    );\n" +
			"\n" +
			"    create table SnapshotEventEntry (\n" +
			"        aggregateIdentifier varchar(255) not null,\n" +
			"        sequenceNumber bigint not null,\n" +
			"        type varchar(255) not null,\n" +
			"        eventIdentifier varchar(255) not null,\n" +
			"        metaData blob,\n" +
			"        payload blob not null,\n" +
			"        payloadRevision varchar(255),\n" +
			"        payloadType varchar(255) not null,\n" +
			"        timeStamp varchar(255) not null,\n" +
			"        primary key (aggregateIdentifier, sequenceNumber, type)\n" +
			"    );";
}
