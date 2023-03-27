/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.test.autoconfigure.redpanda;

import java.net.URI;
import java.util.List;

import org.testcontainers.redpanda.RedpandaContainer;

import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.test.autoconfigure.service.connection.ContainerConnectionSource;

/**
 * {@link ContainerConnectionDetailsFactory} for
 * {@link RedpandaServiceConnection @RedpandaServiceConnection}-annotated
 * {@link RedpandaContainer} fields.
 *
 * @author Eddú Meléndez
 */
class RedpandaContainerConnectionDetailsFactory extends
		ContainerConnectionDetailsFactory<RedpandaServiceConnection, KafkaConnectionDetails, RedpandaContainer> {

	@Override
	protected KafkaConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<RedpandaServiceConnection, KafkaConnectionDetails, RedpandaContainer> source) {
		return new RedpandaContainerConnectionDetails(source);
	}

	/**
	 * {@link KafkaConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class RedpandaContainerConnectionDetails extends ContainerConnectionDetails
			implements KafkaConnectionDetails {

		private final RedpandaContainer container;

		private RedpandaContainerConnectionDetails(
				ContainerConnectionSource<RedpandaServiceConnection, KafkaConnectionDetails, RedpandaContainer> source) {
			super(source);
			this.container = source.getContainer();
		}

		@Override
		public List<Node> getBootstrapNodes() {
			URI uri = URI.create(this.container.getBootstrapServers());
			return List.of(new Node(uri.getHost(), uri.getPort()));
		}

	}

}
