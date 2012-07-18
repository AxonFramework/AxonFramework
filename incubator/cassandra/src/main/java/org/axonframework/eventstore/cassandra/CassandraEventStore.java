package org.axonframework.eventstore.cassandra;


import me.prettyprint.cassandra.serializers.ByteBufferSerializer;
import me.prettyprint.cassandra.serializers.CompositeSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.service.ColumnSliceIterator;
import me.prettyprint.cassandra.service.ThriftKsDef;
import me.prettyprint.cassandra.service.template.ColumnFamilyTemplate;
import me.prettyprint.cassandra.service.template.ColumnFamilyUpdater;
import me.prettyprint.cassandra.service.template.ThriftColumnFamilyTemplate;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.Composite;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.ComparatorType;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.SliceQuery;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.SnapshotEventStore;
import org.axonframework.eventstore.jpa.SimpleSerializedDomainEventData;
import org.axonframework.serializer.SerializedDomainEventMessage;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.joda.time.DateTime;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Ming Fang
 * @author Allard Buijze
 * @since 2.0
 */
public class CassandraEventStore implements SnapshotEventStore {

    private static final int FIRST_RECORD = 0;

    private final Serializer serializer;
    private final Keyspace keyspace;
    private final String eventCFName;
    private final String snapshotCFName;
    private final ColumnFamilyTemplate<String, Composite> eventTemplate;
    private final ColumnFamilyTemplate<String, Composite> snapshotTemplate;

// --------------------------- CONSTRUCTORS ---------------------------

    public CassandraEventStore(Serializer serializer, String clusterName, String keyspaceName, String eventCFName,
                               String hosts) {
        this.serializer = serializer;
        this.eventCFName = eventCFName;
        this.snapshotCFName = eventCFName + "Snapshot";

        //connect to cluster
        Cluster cluster = HFactory.getOrCreateCluster(clusterName, hosts);
        //dynamically create keyspace
        KeyspaceDefinition keyspaceDefinition = cluster.describeKeyspace(keyspaceName);
        if (keyspaceDefinition == null) {
            ColumnFamilyDefinition cfDef = HFactory.createColumnFamilyDefinition(
                    keyspaceName, eventCFName, ComparatorType.COMPOSITETYPE);
            cfDef.setComparatorTypeAlias("(LongType, UTF8Type)");

            ColumnFamilyDefinition snapshotCFDef = HFactory.createColumnFamilyDefinition(
                    keyspaceName, snapshotCFName, ComparatorType.COMPOSITETYPE);
            snapshotCFDef.setComparatorTypeAlias("(LongType, UTF8Type)");

            keyspaceDefinition = HFactory.createKeyspaceDefinition(
                    keyspaceName, ThriftKsDef.DEF_STRATEGY_CLASS, 1, Arrays.asList(cfDef, snapshotCFDef));
            cluster.addKeyspace(keyspaceDefinition, true);
        }
        keyspace = HFactory.createKeyspace(keyspaceName, cluster);

        eventTemplate = new ThriftColumnFamilyTemplate<String, Composite>(
                keyspace, eventCFName, StringSerializer.get(), CompositeSerializer.get());
        snapshotTemplate = new ThriftColumnFamilyTemplate<String, Composite>(
                keyspace, snapshotCFName, StringSerializer.get(), CompositeSerializer.get());
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface EventStore ---------------------

    @Override
    public void appendEvents(String type, DomainEventStream events) {
        while (events.hasNext()) {
            DomainEventMessage event = events.next();
            write(event.getSequenceNumber(), event, eventTemplate);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        String rowKey = identifier.toString();
        DomainEventStream snapshotStream = read(rowKey, snapshotCFName, null);
        DomainEventMessage snapshot = null;
        if (snapshotStream.hasNext()) {
            snapshot = snapshotStream.next();
        }
        return read(rowKey, eventCFName, snapshot);
    }

// --------------------- Interface SnapshotEventStore ---------------------

    @Override
    public void appendSnapshotEvent(String type, DomainEventMessage snapshotEvent) {
        //snapshot always at first record
        write(FIRST_RECORD, snapshotEvent, snapshotTemplate);
    }

// -------------------------- OTHER METHODS --------------------------

    private DomainEventStream read(String identifier, String cfName, DomainEventMessage snapshot) {
        SliceQuery<String, Composite, ByteBuffer> sliceQuery = HFactory.createSliceQuery(
                keyspace, StringSerializer.get(), CompositeSerializer.get(), ByteBufferSerializer.get());
        sliceQuery.setColumnFamily(cfName);
        sliceQuery.setKey(identifier);
        //start from snapshot if any
        Composite start = null;
        if (snapshot != null) {
            start = new Composite(snapshot.getSequenceNumber() + 1);
        }
        ColumnSliceIterator<String, Composite, ByteBuffer> sliceIterator =
                new ColumnSliceIterator<String, Composite, ByteBuffer>(sliceQuery, start, (Composite) null, false);
        return new CassandraDomainEventStream(snapshot, sliceIterator, identifier, serializer);
    }

    private void write(long recordNum, DomainEventMessage event, ColumnFamilyTemplate<String, Composite> template) {
        String rowId = event.getAggregateIdentifier().toString();

        SerializedObject<byte[]> serializedPayload = serializer.serialize(event.getPayload(), byte[].class);
        SerializedObject<byte[]> meta = serializer.serialize(event.getMetaData(), byte[].class);
        String revision = serializedPayload.getType().getRevision();
        if (revision == null) {
            revision = "";
        }

        ColumnFamilyUpdater<String, Composite> updater = template.createUpdater(rowId);
        {
            updater.setByteBuffer(new Composite(recordNum, Fields.ID), ByteBufferUtil.bytes(event.getIdentifier()));
            updater.setByteBuffer(new Composite(recordNum, Fields.SEQ),
                                  ByteBufferUtil.bytes(event.getSequenceNumber()));
            updater.setByteBuffer(new Composite(recordNum, Fields.TIME_STAMP), ByteBufferUtil.bytes(event.getTimestamp()
                                                                                                         .getMillis()));
            updater.setByteBuffer(new Composite(recordNum, Fields.TYPE),
                                  ByteBufferUtil.bytes(serializedPayload.getType().getName()));
            updater.setByteBuffer(new Composite(recordNum, Fields.PAYLOAD_REVISION), ByteBufferUtil.bytes(revision));
            updater.setByteBuffer(new Composite(recordNum, Fields.PAYLOAD),
                                  ByteBuffer.wrap(serializedPayload.getData()));
            updater.setByteBuffer(new Composite(recordNum, Fields.META), ByteBuffer.wrap(meta.getData()));
        }
        template.update(updater);
    }

// -------------------------- INNER CLASSES --------------------------

    private static interface Fields {

        String SEQ = "SEQ";
        String TIME_STAMP = "TS";
        String TYPE = "TYPE";
        String PAYLOAD = "PAY";
        String PAYLOAD_REVISION = "REV";
        String META = "META";
        String ID = "ID";
        int FIELD_COUNT = 7;
    }

    private static class CassandraDomainEventStream implements DomainEventStream {

        private DomainEventMessage next;
        private final ColumnSliceIterator<String, Composite, ByteBuffer> sliceIterator;
        private final String aggregateIdentifier;
        private final Serializer serializer;

        public CassandraDomainEventStream(DomainEventMessage snapshot,
                                          ColumnSliceIterator<String, Composite, ByteBuffer> sliceIterator,
                                          String aggregateIdentifier, Serializer serializer) {
            this.sliceIterator = sliceIterator;
            this.aggregateIdentifier = aggregateIdentifier;
            this.serializer = serializer;
            if (snapshot != null) {
                next = snapshot;
            } else {
                readNext();
            }
        }

        private void readNext() {
            next = null;
            try {
                String id = null;
                long seqNum = 0;
                DateTime timestamp = null;
                String type = null;
                byte[] payload = null;
                String revision = "";
                byte[] meta = null;
                int count = Fields.FIELD_COUNT;
                while (--count >= 0 && sliceIterator.hasNext()) {
                    HColumn<Composite, ByteBuffer> column = sliceIterator.next();
                    String field = column.getName().get(1, StringSerializer.get());
                    //todo: there's got to be a better way to do this
                    if (field.equals(Fields.SEQ)) {
                        seqNum = ByteBufferUtil.toLong(column.getValueBytes());
                    } else if (field.equals(Fields.TIME_STAMP)) {
                        timestamp = new DateTime(ByteBufferUtil.toLong(column.getValueBytes()));
                    } else if (field.equals(Fields.TYPE)) {
                        type = ByteBufferUtil.string(column.getValueBytes());
                    } else if (field.equals(Fields.PAYLOAD)) {
                        payload = ByteBufferUtil.getArray(column.getValueBytes());
                    } else if (field.equals(Fields.PAYLOAD_REVISION)) {
                        revision = ByteBufferUtil.string(column.getValueBytes());
                    } else if (field.equals(Fields.META)) {
                        meta = ByteBufferUtil.getArray(column.getValueBytes());
                    } else if (field.equals(Fields.ID)) {
                        id = ByteBufferUtil.string(column.getValueBytes());
                    }
                }

                if (payload != null) {
                    SimpleSerializedDomainEventData domainEventData = new SimpleSerializedDomainEventData(
                            id,
                            aggregateIdentifier,
                            seqNum,
                            timestamp.toString(),
                            type,
                            revision,
                            payload,
                            meta);
                    next = new SerializedDomainEventMessage(domainEventData, serializer);
//                    System.out.println("read " + type + ":" + aggregateIdentifier + ":" + seqNum);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public DomainEventMessage next() {
            DomainEventMessage retValue = next;
            readNext();
            return retValue;
        }

        @Override
        public DomainEventMessage peek() {
            return next;
        }
    }

}
