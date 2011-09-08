/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.commandbus.distributed.jgroups;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.ChannelClosedException;
import org.jgroups.ChannelNotConnectedException;
import org.jgroups.ExtendedReceiverAdapter;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Allard Buijze
 */
public class JGroupsCommandBus {

    private final JChannel channel;
    private volatile Ring hashRing;
    private AtomicLong counter = new AtomicLong(0);
    private final String segmentId;
    private final JoinCondition joinedCondition = new JoinCondition();

    public JGroupsCommandBus(final JChannel channel) {
        this.channel = channel;
        this.segmentId = UUID.randomUUID().toString();
    }

    public void subscribe() throws ChannelNotConnectedException, ChannelClosedException {
        channel.setReceiver(new ExtendedReceiverAdapter() {
            @Override
            public byte[] getState() {
                try {
                    return Util.objectToByteBuffer(hashRing);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void getState(OutputStream ostream) {
                try {
                    ostream.write(Util.objectToByteBuffer(hashRing));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void setState(InputStream istream) {
                try {
                    setState(IOUtils.toByteArray(istream));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void setState(byte[] state) {
                try {
                    hashRing = (Ring) Util.objectFromByteBuffer(state);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void viewAccepted(View view) {
                Ring newHashRing = hashRing.withMembers(view.getMembers());
                if (!hashRing.equals(newHashRing)) {
                    System.out.println("Looks like we've lost someone");
                    hashRing = newHashRing;
                    printHashRing();
                }
            }

            @Override
            public void receive(Message msg) {
                Object message = msg.getObject();
                if (message instanceof JoinMessage) {
                    JoinMessage joinMessage = (JoinMessage) message;
                    System.out.println(String.format("%s joined as %s with load factor: %s",
                                                     msg.getSrc(),
                                                     joinMessage.getSegmentId(),
                                                     joinMessage.getSegmentCount()));
                    hashRing = hashRing.withMember(msg.getSrc(),
                                                   joinMessage.getSegmentId(),
                                                   joinMessage.getSegmentCount());
                    printHashRing();
                    if (msg.getSrc().equals(channel.getAddress())) {
                        joinedCondition.setJoined();
                        System.out.println("Joining completed");
                    }
                } else {
                    System.out.println(msg.getObject());
                    System.out.println("A message was received. Now received: " + counter.addAndGet(1));
                }
            }
        });
        if (!channel.getState(null, 10000)) {
            // we're the first, so the ring must be empty
            hashRing = Ring.emptyRing();
        }
    }

    public void printHashRing() {
        hashRing.writeTo(channel, System.out);
    }

    public void joinGroup(int loadFactor) throws ChannelNotConnectedException, ChannelClosedException {
        // Let's join the group
        channel.send(null, null, new JoinMessage(loadFactor, segmentId));
        System.out.println("Join message sent");
    }

    public void awaitJoined() throws InterruptedException {
        joinedCondition.await();
    }

    public void awaitJoined(long millis) throws InterruptedException {
        joinedCondition.await(millis);
    }

    public void send(String message) throws ChannelNotConnectedException, ChannelClosedException {
        Address dest = hashRing.getMemberFor(message);
        channel.send(dest, null, message);
    }

    private static class Ring implements Serializable {

        private final SortedMap<String, Address> hashes;

        private Ring(SortedMap<String, Address> hashed) {
            hashes = hashed;
        }

        public synchronized static Ring fromScratch(Address address, String segmentId, int segmentCount) {
            SortedMap<String, Address> hashes = new TreeMap<String, Address>();
            for (int t = 0; t < segmentCount; t++) {
                String hash = hashOf(segmentId + " #" + t);
                hashes.put(hash, address);
            }
            return new Ring(hashes);
        }

        public static Ring emptyRing() {
            return new Ring(new TreeMap<String, Address>());
        }

        public Ring withMember(Address address, String segmentId, int segmentCount) {
            TreeMap<String, Address> newHashes = new TreeMap<String, Address>(hashes);
            Iterator<Map.Entry<String, Address>> iterator = newHashes.entrySet().iterator();
            while (iterator.hasNext()) {
                if (address.equals(iterator.next().getValue())) {
                    iterator.remove();
                }
            }
            for (int t = 0; t < segmentCount; t++) {
                String hash = hashOf(segmentId + " #" + t);
                newHashes.put(hash, address);
            }
            return new Ring(newHashes);
        }

        public Address getMemberFor(String item) {
            String hash = hashOf(item);
            if (hashes.containsKey(hash)) {
                return hashes.get(hash);
            } else {
                SortedMap<String, Address> tailMap = hashes.tailMap(hash);
                if (tailMap.isEmpty()) {
                    return hashes.get(hashes.firstKey());
                } else {
                    return hashes.get(tailMap.firstKey());
                }
            }
        }

        private static String hashOf(String item) {
            return DigestUtils.md5Hex(item);
        }

        public void writeTo(Channel channel, OutputStream out) {
            PrintStream w = new PrintStream(out);
            for (Map.Entry<String, Address> entry : hashes.entrySet()) {
                w.println(entry.getKey() + ": " + channel.getName(entry.getValue()));
            }
        }

        public Ring withMembers(Vector<Address> members) {
            Set<Address> activeMembers = new HashSet<Address>(members);
            SortedMap<String, Address> newHashes = new TreeMap<String, Address>();
            for (Map.Entry<String, Address> entry : hashes.entrySet()) {
                if (activeMembers.contains(entry.getValue())) {
                    newHashes.put(entry.getKey(), entry.getValue());
                }
            }
            return new Ring(newHashes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Ring ring = (Ring) o;

            if (!hashes.equals(ring.hashes)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return hashes.hashCode();
        }
    }

    public final class JoinCondition {

        // Guarded by "this"
        private boolean isJoined;

        public synchronized void await() throws InterruptedException {
            while (!isJoined) {
                wait();
            }
        }

        public synchronized void await(long millis) throws InterruptedException {
            while (!isJoined) {
                wait(millis);
            }
        }

        private synchronized void setJoined() {
            this.isJoined = true;
            notifyAll();
        }
    }
}
