/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataStoring;
import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.ServiceProviderTestHelper;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LifecycleExtensionTest {

	@Mock
	ExtensionApi extensionApi;

	@Mock
	DataStoring dataStoring;

	@Mock
	NamedCollection lifecycleDataStore;

	@Mock
	DeviceInforming deviceInfoService;

	@Mock
	LifecycleState mockLifecycleState;

	@Mock
	LifecycleV2Extension mockLifecycleV2Extension;

	private final long currentTimestampInMilliSeconds = System.currentTimeMillis();
	private final long currentTimestampInSeconds = TimeUnit.MILLISECONDS.toSeconds(currentTimestampInMilliSeconds);
	private LifecycleExtension lifecycle;

	private static final String DATASTORE_KEY_INSTALL_DATE = "InstallDate";
	private static final String DATASTORE_KEY_PAUSE_DATE = "PauseDate";
	private static final String DATASTORE_KEY_SUCCESSFUL_CLOSE = "SuccessfulClose";


	private static final String SESSION_START_TIMESTAMP = "starttimestampmillis";
	private static final String MAX_SESSION_LENGTH      = "maxsessionlength";
	private static final long MAX_SESSION_LENGTH_SECONDS = TimeUnit.DAYS.toSeconds(7);
	private static final String ADDITIONAL_CONTEXT_DATA = "additionalcontextdata";

	private static final String LIFECYCLE_ACTION_KEY = "action";
	private static final String LIFECYCLE_PAUSE = "pause";
	private static final String LIFECYCLE_START = "start";

	private static final String LIFECYCLE_CONFIG_SESSION_TIMEOUT = "lifecycle.sessionTimeout";
	private static final String CONFIGURATION_MODULE_NAME = "com.adobe.module.configuration";

	private static final String IDENTITY_MODULE_NAME = "com.adobe.module.identity";
	private static final String ADVERTISING_IDENTIFIER = "advertisingidentifier";

	private static final String EVENT_TYPE_GENERIC_LIFECYCLE = "com.adobe.eventType.generic.lifecycle";
	private static final String EVENT_TYPE_LIFECYCLE = "com.adobe.eventType.lifecycle";
	private static final String EVENT_SOURCE_REQUEST_CONTENT = "com.adobe.eventSource.requestContent";


	@Before
	public void beforeEach() {
		ServiceProviderTestHelper.setDataStoring(dataStoring);
		when(dataStoring.getNamedCollection(eq("AdobeMobile_Lifecycle"))).thenReturn(lifecycleDataStore);
		ServiceProviderTestHelper.setDeviceInfoService(deviceInfoService);
		LifecycleTestHelper.initDeviceInfoService(deviceInfoService);

		Map<String, Object> configurationSharedState = new HashMap<>();
		configurationSharedState.put(LIFECYCLE_CONFIG_SESSION_TIMEOUT, 200L);
		when(extensionApi.getSharedState(
				eq(CONFIGURATION_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(new SharedStateResult(SharedStateStatus.SET, configurationSharedState));

		lifecycle = new LifecycleExtension(extensionApi, mockLifecycleState, mockLifecycleV2Extension);
	}

	@Test
	public void readyForEvent_ConfigurationSharedStateSet() {
		Event event = new Event.Builder("Lifecycle_queueEvent_Happy",
				EVENT_TYPE_GENERIC_LIFECYCLE,
				EVENT_SOURCE_REQUEST_CONTENT)
				.build();

		assertTrue(lifecycle.readyForEvent(event));
	}

	@Test
	public void readyForEvent_ConfigurationSharedStateNotSet() {
		// set config shared state to pending
		when(extensionApi.getSharedState(
				eq(CONFIGURATION_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>()));

		Event event = new Event.Builder("Lifecycle_queueEvent_Happy",
				EVENT_TYPE_GENERIC_LIFECYCLE,
				EVENT_SOURCE_REQUEST_CONTENT)
				.build();

		assertFalse(lifecycle.readyForEvent(event));
	}

	@Test
	public void handleLifecycleRequestEvent_NullEvent() {
		lifecycle.handleLifecycleRequestEvent(null);
		verifyNoInteractions(mockLifecycleState);
	}

	@Test
	public void handleLifecycleRequestEvent_ConfigurationSharedStateNull() {
		when(extensionApi.getSharedState(
				eq(CONFIGURATION_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(null);

		lifecycle.handleLifecycleRequestEvent(createStartEvent(null, currentTimestampInMilliSeconds));

		verifyNoInteractions(mockLifecycleState);
	}

	@Test
	public void handleLifecycleRequestEvent_ConfigurationSharedStatePending() {
		when(extensionApi.getSharedState(
				eq(CONFIGURATION_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>()));

		lifecycle.handleLifecycleRequestEvent(createStartEvent(null, currentTimestampInMilliSeconds));

		verifyNoInteractions(mockLifecycleState);
	}

	@Test
	public void handleLifecycleRequestEvent_EventDataEmpty() {
		Event lifecycleEvent = new Event.Builder(null, EVENT_TYPE_LIFECYCLE, EVENT_SOURCE_REQUEST_CONTENT)
				.setTimestamp(currentTimestampInMilliSeconds)
				.build();

		lifecycle.handleLifecycleRequestEvent(lifecycleEvent);

		verifyNoInteractions(mockLifecycleState);
	}

	@Test
	public void handleLifecycleRequestEvent_LifecycleStart_FirstLaunch() {
		Event lifecycleStartEvent = createStartEvent(null, currentTimestampInMilliSeconds);

		lifecycle.handleLifecycleRequestEvent(lifecycleStartEvent);

		ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Map<String, String>> additionalDataCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> adIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> sessionTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> isInstallCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(mockLifecycleState, times(1)).start(startTimeCaptor.capture(),
				additionalDataCaptor.capture(),
				adIdCaptor.capture(),
				sessionTimeoutCaptor.capture(),
				isInstallCaptor.capture());
		assertEquals(currentTimestampInSeconds, startTimeCaptor.getValue().longValue());
		assertNull(additionalDataCaptor.getValue());
		assertNull(adIdCaptor.getValue());
		assertEquals(200L, sessionTimeoutCaptor.getValue().longValue());
		assertTrue(isInstallCaptor.getValue());
	}

	@Test
	public void handleLifecycleRequestEvent_LifecycleStart_SubsequentLaunch() {
		when(lifecycleDataStore.contains(eq(DATASTORE_KEY_INSTALL_DATE))).thenReturn(true);
		Event lifecycleStartEvent = createStartEvent(null, currentTimestampInMilliSeconds);

		lifecycle.handleLifecycleRequestEvent(lifecycleStartEvent);

		ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Map<String, String>> additionalDataCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> adIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> sessionTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> isInstallCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(mockLifecycleState, times(1)).start(startTimeCaptor.capture(),
				additionalDataCaptor.capture(),
				adIdCaptor.capture(),
				sessionTimeoutCaptor.capture(),
				isInstallCaptor.capture());
		assertEquals(currentTimestampInSeconds, startTimeCaptor.getValue().longValue());
		assertNull(additionalDataCaptor.getValue());
		assertNull(adIdCaptor.getValue());
		assertEquals(200L, sessionTimeoutCaptor.getValue().longValue());
		assertFalse(isInstallCaptor.getValue());
	}

	@Test
	public void handleLifecycleRequestEvent_LifecycleStart_AdditionalData() {
		Map<String, String> additionalData = new HashMap<>();
		additionalData.put("testKey", "testVal");
		Event lifecycleStartEvent = createStartEvent(additionalData, currentTimestampInMilliSeconds);

		lifecycle.handleLifecycleRequestEvent(lifecycleStartEvent);

		ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Map<String, String>> additionalDataCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> adIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> sessionTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> isInstallCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(mockLifecycleState, times(1)).start(startTimeCaptor.capture(),
				additionalDataCaptor.capture(),
				adIdCaptor.capture(),
				sessionTimeoutCaptor.capture(),
				isInstallCaptor.capture());
		assertEquals(currentTimestampInSeconds, startTimeCaptor.getValue().longValue());
		assertEquals(additionalData, additionalDataCaptor.getValue());
		assertNull(adIdCaptor.getValue());
		assertEquals(200L, sessionTimeoutCaptor.getValue().longValue());
		assertTrue(isInstallCaptor.getValue());
	}

	@Test
	public void handleLifecycleRequestEvent_LifecycleStart_IdentitySharedStatePending() {
		when(extensionApi.getSharedState(
				eq(IDENTITY_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>()));

		Map<String, String> additionalData = new HashMap<>();
		additionalData.put("testKey", "testVal");
		Event lifecycleStartEvent = createStartEvent(additionalData, currentTimestampInMilliSeconds);

		lifecycle.handleLifecycleRequestEvent(lifecycleStartEvent);

		ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Map<String, String>> additionalDataCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> adIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> sessionTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> isInstallCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(mockLifecycleState, times(1)).start(startTimeCaptor.capture(),
				additionalDataCaptor.capture(),
				adIdCaptor.capture(),
				sessionTimeoutCaptor.capture(),
				isInstallCaptor.capture());
		assertEquals(currentTimestampInSeconds, startTimeCaptor.getValue().longValue());
		assertEquals(additionalData, additionalDataCaptor.getValue());
		assertNull(adIdCaptor.getValue());
		assertEquals(200L, sessionTimeoutCaptor.getValue().longValue());
		assertTrue(isInstallCaptor.getValue());
	}

	@Test
	public void handleLifecycleRequestEvent_LifecycleStart_AdIdSet() {
		Map<String, Object> identitySharedState = new HashMap<>();
		identitySharedState.put(ADVERTISING_IDENTIFIER, "testAdid");
		when(extensionApi.getSharedState(
				eq(IDENTITY_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(new SharedStateResult(SharedStateStatus.SET, identitySharedState));

		Map<String, String> additionalData = new HashMap<>();
		additionalData.put("testKey", "testVal");
		Event lifecycleStartEvent = createStartEvent(additionalData, currentTimestampInMilliSeconds);

		lifecycle.handleLifecycleRequestEvent(lifecycleStartEvent);

		ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Map<String, String>> additionalDataCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> adIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> sessionTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> isInstallCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(mockLifecycleState, times(1)).start(startTimeCaptor.capture(),
				additionalDataCaptor.capture(),
				adIdCaptor.capture(),
				sessionTimeoutCaptor.capture(),
				isInstallCaptor.capture());
		assertEquals(currentTimestampInSeconds, startTimeCaptor.getValue().longValue());
		assertEquals(additionalData, additionalDataCaptor.getValue());
		assertEquals("testAdid", adIdCaptor.getValue());
		assertEquals(200L, sessionTimeoutCaptor.getValue().longValue());
		assertTrue(isInstallCaptor.getValue());
	}

	@Test
	public void handleLifecycleRequestEvent_LifecycleStart_SessionTimeoutNotSet() {
		when(extensionApi.getSharedState(
				eq(CONFIGURATION_MODULE_NAME),
				any(),
				eq(false),
				eq(SharedStateResolution.ANY)
		)).thenReturn(new SharedStateResult(SharedStateStatus.SET, new HashMap<>()));

		Event lifecycleStartEvent = createStartEvent(null, currentTimestampInMilliSeconds);

		lifecycle.handleLifecycleRequestEvent(lifecycleStartEvent);

		ArgumentCaptor<Long> startTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Map<String, String>> additionalDataCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> adIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> sessionTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> isInstallCaptor = ArgumentCaptor.forClass(Boolean.class);

		verify(mockLifecycleState, times(1)).start(startTimeCaptor.capture(),
				additionalDataCaptor.capture(),
				adIdCaptor.capture(),
				sessionTimeoutCaptor.capture(),
				isInstallCaptor.capture());
		assertEquals(currentTimestampInSeconds, startTimeCaptor.getValue().longValue());
		assertNull(additionalDataCaptor.getValue());
		assertNull(adIdCaptor.getValue());
		assertEquals(300L, sessionTimeoutCaptor.getValue().longValue());
		assertTrue(isInstallCaptor.getValue());
	}

	@Test
	public void handleLifecycleRequestEvent_LifecyclePause() {
		Map<String, Object> eventData = new HashMap<>();
		eventData.put(LIFECYCLE_ACTION_KEY, LIFECYCLE_PAUSE);
		Event lifecyclePauseEvent = new Event.Builder(null, EVENT_TYPE_LIFECYCLE, EVENT_SOURCE_REQUEST_CONTENT)
				.setTimestamp(currentTimestampInMilliSeconds)
				.setEventData(eventData)
				.build();

		lifecycle.handleLifecycleRequestEvent(lifecyclePauseEvent);

		verify(mockLifecycleState, times(1)).pause(lifecyclePauseEvent);
		verify(mockLifecycleV2Extension, times(1)).pause(lifecyclePauseEvent);
	}


	@Test
	public void handleLifecycleRequestEvent_InvalidEventData() {
		Map<String, Object> eventData = new HashMap<>();
		eventData.put(LIFECYCLE_ACTION_KEY, "invalid_action");
		Event lifecycleRequestEvent = new Event.Builder(null, EVENT_TYPE_LIFECYCLE, EVENT_SOURCE_REQUEST_CONTENT)
				.setTimestamp(currentTimestampInMilliSeconds)
				.setEventData(eventData)
				.build();
		lifecycle.handleLifecycleRequestEvent(lifecycleRequestEvent);

		verify(lifecycleDataStore, never()).setBoolean(eq(DATASTORE_KEY_SUCCESSFUL_CLOSE), anyBoolean());
		verify(lifecycleDataStore, never()).setLong(eq(DATASTORE_KEY_PAUSE_DATE), anyLong());
	}

	@Test
	public void handleEventHubBootEvent() {
		Event eventHubBootEvent = new Event.Builder(null, EventType.HUB, EventSource.BOOTED)
				.setTimestamp(currentTimestampInMilliSeconds)
				.build();
		lifecycle.handleEventHubBootEvent(eventHubBootEvent);

		verify(mockLifecycleState, times(1)).computeBootData(eq(currentTimestampInSeconds));

		ArgumentCaptor<Map<String, Object>> lifecycleStateCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
		verify(extensionApi, times(1)).createSharedState(lifecycleStateCaptor.capture(), eventCaptor.capture());
		assertEquals(eventHubBootEvent, eventCaptor.getValue());
		assertEquals(0L, lifecycleStateCaptor.getValue().get(SESSION_START_TIMESTAMP));
		assertEquals(MAX_SESSION_LENGTH_SECONDS, lifecycleStateCaptor.getValue().get(MAX_SESSION_LENGTH));
	}

	@Test
	public void handleUpdateLastKnownTimestamp() {
		Event lifecycleRequestEvent = new Event.Builder(null, EVENT_TYPE_LIFECYCLE, EVENT_SOURCE_REQUEST_CONTENT)
				.setTimestamp(currentTimestampInMilliSeconds)
				.build();
		lifecycle.updateLastKnownTimestamp(lifecycleRequestEvent);
		verify(mockLifecycleV2Extension, times(1)).updateLastKnownTimestamp(lifecycleRequestEvent);
	}

	private Event createStartEvent(final Map<String, String> additionalData, final long timestamp) {
		Map<String, Object> eventData = new HashMap<>();
		eventData.put(LIFECYCLE_ACTION_KEY,
				LIFECYCLE_START);
		eventData.put(ADDITIONAL_CONTEXT_DATA, additionalData);
		return new Event.Builder(null, EVENT_TYPE_GENERIC_LIFECYCLE, EVENT_SOURCE_REQUEST_CONTENT)
				.setTimestamp(timestamp)
				.setEventData(eventData)
				.build();
	}
}
