package org.axonframework.eventstore.jpa;

import java.util.Iterator;
import java.util.Map;

import javax.persistence.EntityManager;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.SerializedObject;

public class CustomEventEntryStore implements EventEntryStore {

	@Override
	public void persistEvent(String aggregateType, DomainEventMessage event,
			SerializedObject serializedPayload,
			SerializedObject serializedMetaData, EntityManager entityManager) {
		// TODO Auto-generated method stub

	}

	@Override
	public SerializedDomainEventData loadLastSnapshotEvent(
			String aggregateType, Object identifier, EntityManager entityManager) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<? extends SerializedDomainEventData> fetchAggregateStream(
			String aggregateType, Object identifier, long firstSequenceNumber,
			int batchSize, EntityManager entityManager) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<? extends SerializedDomainEventData> fetchFiltered(
			String whereClause, Map<String, Object> parameters, int batchSize,
			EntityManager entityManager) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void pruneSnapshots(String type,
			DomainEventMessage mostRecentSnapshotEvent,
			int maxSnapshotsArchived, EntityManager entityManager) {
		// TODO Auto-generated method stub

	}

	@Override
	public void persistSnapshot(String aggregateType,
			DomainEventMessage snapshotEvent,
			SerializedObject serializedPayload,
			SerializedObject serializedMetaData, EntityManager entityManager) {
		// TODO Auto-generated method stub

	}

}
