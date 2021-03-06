/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.zookeeper.event;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.leader.event.AbstractLeaderEvent;
import org.springframework.integration.leader.event.DefaultLeaderEventPublisher;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.integration.support.SmartLifecycleRoleController;
import org.springframework.integration.zookeeper.ZookeeperTestSupport;
import org.springframework.integration.zookeeper.leader.LeaderInitiator;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Gary Russell
 * @since 4.2
 *
 */
public class ZookeeperLeaderTests extends ZookeeperTestSupport {

	private final BlockingQueue<AbstractLeaderEvent> events = new LinkedBlockingQueue<AbstractLeaderEvent>();

	private final SourcePollingChannelAdapter adapter = buildChannelAdapter();

	private final SmartLifecycleRoleController controller = new SmartLifecycleRoleController(
			Collections.singletonList("sitest"), Collections.<SmartLifecycle>singletonList(this.adapter));

	@Test
	public void testLeader() throws Exception {
		LeaderEventPublisher publisher = publisher();
		DefaultCandidate candidate1 = new DefaultCandidate("foo", "sitest");
		LeaderInitiator initiator1 = new LeaderInitiator(this.client, candidate1, "/sitest");
		initiator1.setLeaderEventPublisher(publisher);
		initiator1.start();
		DefaultCandidate candidate2 = new DefaultCandidate("bar", "sitest");
		LeaderInitiator initiator2 = new LeaderInitiator(this.client, candidate2, "/sitest");
		initiator2.setLeaderEventPublisher(publisher);
		initiator2.start();
		AbstractLeaderEvent event = this.events.poll(10, TimeUnit.SECONDS);
		assertNotNull(event);
		assertThat(event, instanceOf(OnGrantedEvent.class));
		event.getContext().yield();

		assertTrue(this.adapter.isRunning());

		event = this.events.poll(10, TimeUnit.SECONDS);
		assertNotNull(event);
		assertThat(event, instanceOf(OnRevokedEvent.class));

		assertFalse(this.adapter.isRunning());

		event = this.events.poll(10, TimeUnit.SECONDS);
		assertNotNull(event);
		assertThat(event, instanceOf(OnGrantedEvent.class));

		assertTrue(this.adapter.isRunning());

		initiator1.stop();
		initiator2.stop();
		event = this.events.poll(10, TimeUnit.SECONDS);
		assertNotNull(event);
		assertThat(event, instanceOf(OnRevokedEvent.class));

		assertFalse(this.adapter.isRunning());
	}

	private LeaderEventPublisher publisher() {
		return new DefaultLeaderEventPublisher(new ApplicationEventPublisher() {

			@Override
			public void publishEvent(Object event) {
			}

			@Override
			public void publishEvent(ApplicationEvent event) {
				AbstractLeaderEvent leadershipEvent = (AbstractLeaderEvent) event;
				controller.onApplicationEvent((AbstractLeaderEvent) event);
				events.add(leadershipEvent);
			}

		});
	}

	private SourcePollingChannelAdapter buildChannelAdapter() {
		SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
		adapter.setSource(new MessageSource<String>() {

			@Override
			public Message<String> receive() {
				return new GenericMessage<String>("foo");
			}
		});
		adapter.setOutputChannel(new QueueChannel());
		adapter.setTrigger(new PeriodicTrigger(10000));
		adapter.setBeanFactory(mock(BeanFactory.class));
		ThreadPoolTaskScheduler sched = new ThreadPoolTaskScheduler();
		sched.afterPropertiesSet();
		adapter.setTaskScheduler(sched);
		adapter.afterPropertiesSet();
		return adapter;
	}

}
