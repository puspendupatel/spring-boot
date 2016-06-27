/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.autoconfigure.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.ReconnectionPolicy;
import com.datastax.driver.core.policies.RetryPolicy;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Cassandra.
 *
 * @author Julien Dubois
 * @author Phillip Webb
 * @since 1.3.0
 */
@Configuration
@ConditionalOnClass({ Cluster.class })
@EnableConfigurationProperties(CassandraProperties.class)
public class CassandraAutoConfiguration {

	private final CassandraProperties properties;

	public CassandraAutoConfiguration(CassandraProperties properties) {
		this.properties = properties;
	}

	@Bean
	@ConditionalOnMissingBean
	public Cluster cluster() {
		CassandraProperties properties = this.properties;
		Cluster.Builder builder = Cluster.builder()
				.withClusterName(properties.getClusterName())
				.withPort(properties.getPort());
		if (properties.getUsername() != null) {
			builder.withCredentials(properties.getUsername(), properties.getPassword());
		}
		if (properties.getCompression() != null) {
			builder.withCompression(properties.getCompression());
		}
		if (properties.getLoadBalancingPolicy() != null) {
			LoadBalancingPolicy policy = instantiate(properties.getLoadBalancingPolicy());
			builder.withLoadBalancingPolicy(policy);
		}
		builder.withQueryOptions(getQueryOptions());
		if (properties.getReconnectionPolicy() != null) {
			ReconnectionPolicy policy = instantiate(properties.getReconnectionPolicy());
			builder.withReconnectionPolicy(policy);
		}
		if (properties.getRetryPolicy() != null) {
			RetryPolicy policy = instantiate(properties.getRetryPolicy());
			builder.withRetryPolicy(policy);
		}
		builder.withSocketOptions(getSocketOptions());
		if (properties.isSsl()) {
			builder.withSSL();
		}
		builder.withPoolingOptions(getPoolingOptions());
		String points = properties.getContactPoints();
		builder.addContactPoints(StringUtils.commaDelimitedListToStringArray(points));
		return builder.build();
	}

	public static <T> T instantiate(Class<T> type) {
		return BeanUtils.instantiate(type);
	}

	private QueryOptions getQueryOptions() {
		QueryOptions options = new QueryOptions();
		CassandraProperties properties = this.properties;
		if (properties.getConsistencyLevel() != null) {
			options.setConsistencyLevel(properties.getConsistencyLevel());
		}
		if (properties.getSerialConsistencyLevel() != null) {
			options.setSerialConsistencyLevel(properties.getSerialConsistencyLevel());
		}
		options.setFetchSize(properties.getFetchSize());
		return options;
	}

	private SocketOptions getSocketOptions() {
		SocketOptions options = new SocketOptions();
		options.setConnectTimeoutMillis(this.properties.getConnectTimeoutMillis());
		options.setReadTimeoutMillis(this.properties.getReadTimeoutMillis());
		if (this.properties.isKeepAlive() != null) {
			options.setKeepAlive(this.properties.isKeepAlive());
		}
		return options;
	}

	private PoolingOptions getPoolingOptions() {
		PoolingOptions options = new PoolingOptions();
		if (this.properties.getCorePoolLocal() != null) {
			options.setCoreConnectionsPerHost(HostDistance.LOCAL,
					this.properties.getCorePoolLocal());
		}
		if (this.properties.getMaxPoolLocal() != null) {
			options.setMaxConnectionsPerHost(HostDistance.LOCAL,
					this.properties.getMaxPoolLocal());
		}
		if (this.properties.getCorePoolRemote() != null) {
			options.setCoreConnectionsPerHost(HostDistance.REMOTE,
					this.properties.getCorePoolRemote());
		}
		if (this.properties.getMaxPoolRemote() != null) {
			options.setMaxConnectionsPerHost(HostDistance.REMOTE,
					this.properties.getMaxPoolRemote());
		}
		if (this.properties.getNewConnectionThresholdLocal() != null) {
			options.setNewConnectionThreshold(HostDistance.LOCAL,
					this.properties.getNewConnectionThresholdLocal());
		}
		if (this.properties.getNewConnectionThresholdRemote() != null) {
			options.setNewConnectionThreshold(HostDistance.REMOTE,
					this.properties.getNewConnectionThresholdRemote());
		}
		if (this.properties.getMaxRequestsPerConnectionLocal() != null) {
			options.setMaxRequestsPerConnection(HostDistance.LOCAL,
					this.properties.getMaxRequestsPerConnectionLocal());
		}
		if (this.properties.getMaxRequestsPerConnectionRemote() != null) {
			options.setMaxRequestsPerConnection(HostDistance.REMOTE,
					this.properties.getMaxRequestsPerConnectionRemote());
		}
		return options;
	}
}
