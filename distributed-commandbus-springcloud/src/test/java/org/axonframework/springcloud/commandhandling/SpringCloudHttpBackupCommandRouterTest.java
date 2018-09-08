/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.springcloud.commandhandling;

import com.google.common.collect.ImmutableList;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SpringCloudHttpBackupCommandRouterTest {

    private static final int LOAD_FACTOR = 1;
    private static final String SERVICE_INSTANCE_ID = "SERVICE_ID";
    private static final URI SERVICE_INSTANCE_URI = URI.create("endpoint");
    private static final Predicate<? super CommandMessage<?>> COMMAND_NAME_FILTER = AcceptAll.INSTANCE;

    private SpringCloudHttpBackupCommandRouter testSubject;

    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private Registration localServiceInstance;
    @Mock
    private RoutingStrategy routingStrategy;
    @Mock
    private RestTemplate restTemplate;
    private String messageRoutingInformationEndpoint = "/message-routing-information";

    private URI testRemoteUri = URI.create("http://remote");
    private MessageRoutingInformation expectedMessageRoutingInfo;
    @Captor
    private ArgumentCaptor<URI> uriArgumentCaptor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        when(localServiceInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(localServiceInstance.getUri()).thenReturn(SERVICE_INSTANCE_URI);
        when(localServiceInstance.getMetadata()).thenReturn(new HashMap<>());

        when(discoveryClient.getServices()).thenReturn(Collections.singletonList(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(Collections.singletonList(localServiceInstance));

        expectedMessageRoutingInfo =
                new MessageRoutingInformation(LOAD_FACTOR, COMMAND_NAME_FILTER, new XStreamSerializer());

        ResponseEntity<MessageRoutingInformation> responseEntity = mock(ResponseEntity.class);
        when(responseEntity.getBody()).thenReturn(expectedMessageRoutingInfo);
        URI expectedRemoteUri = URI.create("http://remote/message-routing-information");
        when(restTemplate.exchange(
                eq(expectedRemoteUri), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(MessageRoutingInformation.class)
        )).thenReturn(responseEntity);

        testSubject = SpringCloudHttpBackupCommandRouter.builder()
                                                        .discoveryClient(discoveryClient)
                                                        .localServiceInstance(localServiceInstance)
                                                        .routingStrategy(routingStrategy)
                                                        .restTemplate(restTemplate)
                                                        .messageRoutingInformationEndpoint(
                                                                messageRoutingInformationEndpoint
                                                        ).build();
    }

    @Test
    public void testGetLocalMessageRoutingInformationReturnsNullIfMembershipIsNeverUpdated() {
        assertNull(testSubject.getLocalMessageRoutingInformation());
    }

    @Test
    public void testGetLocalMessageRoutingInformationReturnsMessageRoutingInformation() {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        MessageRoutingInformation result = testSubject.getLocalMessageRoutingInformation();

        assertEquals(expectedMessageRoutingInfo, result);
    }

    @Test
    public void testGetMessageRoutingInformationReturnsLocalMessageRoutingInformationIfSimpleMemberIsLocal() {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(localServiceInstance);

        assertTrue(result.isPresent());
        assertEquals(expectedMessageRoutingInfo, result.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetMessageRoutingInformationThrowsIllegalArgumentExceptionIfEndpointIsMissing() {
        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(null);

        testSubject.getMessageRoutingInformation(remoteInstance);
    }

    @Test
    public void testGetMessageRoutingInformationRequestsMessageRoutingInformation() {
        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(testRemoteUri);

        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(remoteInstance);

        assertTrue(result.isPresent());
        assertEquals(expectedMessageRoutingInfo, result.get());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals(messageRoutingInformationEndpoint, resultUri.getPath());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetMessageRoutingInformationReturnsEmptyOptionalFromNonAxonServiceInstanceRequest() {
        ServiceInstance nonAxonInstance = mock(ServiceInstance.class);
        when(nonAxonInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(nonAxonInstance.getUri()).thenReturn(URI.create("http://non-axon"));

        ResponseEntity<MessageRoutingInformation> responseEntity = mock(ResponseEntity.class);
        URI testRemoteUri = URI.create("http://non-axon/message-routing-information");
        when(restTemplate.exchange(
                eq(testRemoteUri), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(MessageRoutingInformation.class)
        )).thenReturn(responseEntity);

        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(nonAxonInstance);

        assertFalse(result.isPresent());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals(messageRoutingInformationEndpoint, resultUri.getPath());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetMessageRoutingInformationReturnsEmptyOptionalFromNonAxonServiceInstanceRequestWhichThrowsAnHttpClientErrorException() {
        ServiceInstance nonAxonInstance = mock(ServiceInstance.class);
        when(nonAxonInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(nonAxonInstance.getUri()).thenReturn(URI.create("http://non-axon"));

        URI testRemoteUri = URI.create("http://non-axon/message-routing-information");
        when(restTemplate.exchange(
                eq(testRemoteUri), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(MessageRoutingInformation.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(nonAxonInstance);

        assertFalse(result.isPresent());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals(messageRoutingInformationEndpoint, resultUri.getPath());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetMessageRoutingInformationReturnsUnreachableMessageRoutingInformationFromServiceInstanceRequestWhichThrowsAnException() {
        MessageRoutingInformation expectedMessageRoutingInfo =
                new MessageRoutingInformation(0, DenyAll.INSTANCE, testSubject.serializer);

        ServiceInstance nonAxonInstance = mock(ServiceInstance.class);
        when(nonAxonInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(nonAxonInstance.getUri()).thenReturn(URI.create("http://faulty-axon"));

        ResponseEntity<MessageRoutingInformation> responseEntity = mock(ResponseEntity.class);
        when(responseEntity.getBody()).thenReturn(expectedMessageRoutingInfo);
        URI testRemoteUri = URI.create("http://faulty-axon/message-routing-information");
        when(restTemplate.exchange(
                eq(testRemoteUri), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(MessageRoutingInformation.class)
        )).thenReturn(responseEntity);

        Optional<MessageRoutingInformation> result = testSubject.getMessageRoutingInformation(nonAxonInstance);

        assertTrue(result.isPresent());
        assertEquals(expectedMessageRoutingInfo, result.get());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals(messageRoutingInformationEndpoint, resultUri.getPath());
    }

    @Test
    public void testUpdateMembershipsOnHeartbeatEventRequestsMessageRoutingInformationByHttpRequest() {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        ServiceInstance remoteInstance = mock(ServiceInstance.class);
        when(remoteInstance.getServiceId()).thenReturn(SERVICE_INSTANCE_ID);
        when(remoteInstance.getUri()).thenReturn(testRemoteUri);

        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID));
        when(discoveryClient.getInstances(SERVICE_INSTANCE_ID))
                .thenReturn(ImmutableList.of(localServiceInstance, remoteInstance));

        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        verify(discoveryClient).getServices();
        verify(discoveryClient).getInstances(SERVICE_INSTANCE_ID);
        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(MessageRoutingInformation.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdateMembershipsOnHeartbeatEventDoesNotRequestInfoFromBlackListedServiceInstance() {
        testSubject.updateMembership(LOAD_FACTOR, COMMAND_NAME_FILTER);

        String nonAxonServiceInstanceId = "nonAxonInstance";
        ServiceInstance nonAxonInstance = mock(ServiceInstance.class);
        when(nonAxonInstance.getServiceId()).thenReturn(nonAxonServiceInstanceId);
        when(nonAxonInstance.getUri()).thenReturn(URI.create("http://non-axon"));

        when(discoveryClient.getServices()).thenReturn(ImmutableList.of(SERVICE_INSTANCE_ID, nonAxonServiceInstanceId));
        when(discoveryClient.getInstances(nonAxonServiceInstanceId)).thenReturn(ImmutableList.of(nonAxonInstance));

        ResponseEntity<MessageRoutingInformation> responseEntity = mock(ResponseEntity.class);
        URI testRemoteUri = URI.create("http://non-axon/message-routing-information");
        when(restTemplate.exchange(
                eq(testRemoteUri), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(MessageRoutingInformation.class)
        )).thenReturn(responseEntity);

        // First update - black lists 'nonAxonServiceInstance' as it does not contain any message routing information
        testSubject.updateMemberships(mock(HeartbeatEvent.class));
        // Second update
        testSubject.updateMemberships(mock(HeartbeatEvent.class));

        verify(discoveryClient, times(2)).getServices();
        verify(discoveryClient, times(2)).getInstances(nonAxonServiceInstanceId);
        verify(discoveryClient, times(2)).getInstances(SERVICE_INSTANCE_ID);
        // Just once, only for the nonAxonInstance. The default serviceInstance is local, so no rest call is done
        verify(restTemplate, times(1)).exchange(uriArgumentCaptor.capture(),
                                                eq(HttpMethod.GET),
                                                eq(HttpEntity.EMPTY),
                                                eq(MessageRoutingInformation.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals(messageRoutingInformationEndpoint, resultUri.getPath());
    }
}
