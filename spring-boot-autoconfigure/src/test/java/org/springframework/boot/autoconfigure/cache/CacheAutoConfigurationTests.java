/*
 * Copyright 2012-2017 the original author or authors.
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

package org.springframework.boot.autoconfigure.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.bucket.BucketManager;
import com.couchbase.client.spring.cache.CouchbaseCache;
import com.couchbase.client.spring.cache.CouchbaseCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import net.sf.ehcache.Status;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.jcache.embedded.JCachingProvider;
import org.infinispan.spring.provider.SpringEmbeddedCacheManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.support.MockCachingProvider;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.test.context.ApplicationContextTester;
import org.springframework.boot.test.context.AssertableApplicationContext;
import org.springframework.boot.test.context.ContextConsumer;
import org.springframework.boot.testsupport.runner.classpath.ClassPathExclusions;
import org.springframework.boot.testsupport.runner.classpath.ModifiedClassPathRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link CacheAutoConfiguration}.
 *
 * @author Stephane Nicoll
 * @author Eddú Meléndez
 * @author Mark Paluch
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions("hazelcast-client-*.jar")
public class CacheAutoConfigurationTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	private final ApplicationContextTester context = new ApplicationContextTester()
			.withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

	@Test
	public void noEnableCaching() {
		this.context.withUserConfiguration(EmptyConfiguration.class).run((loaded) -> {
			assertThat(loaded).doesNotHaveBean(CacheManager.class);
		});
	}

	@Test
	public void cacheManagerBackOff() {
		this.context.withUserConfiguration(CustomCacheManagerConfiguration.class)
				.run((loaded) -> {
					assertThat(getCacheManager(loaded, ConcurrentMapCacheManager.class)
							.getCacheNames()).containsOnly("custom1");
				});
	}

	@Test
	public void cacheManagerFromSupportBackOff() {
		this.context
				.withUserConfiguration(CustomCacheManagerFromSupportConfiguration.class)
				.run((loaded) -> {
					assertThat(getCacheManager(loaded, ConcurrentMapCacheManager.class)
							.getCacheNames()).containsOnly("custom1");
				});
	}

	@Test
	public void cacheResolverFromSupportBackOff() throws Exception {
		this.context
				.withUserConfiguration(CustomCacheResolverFromSupportConfiguration.class)
				.run((loaded) -> {
					assertThat(loaded).doesNotHaveBean(CacheManager.class);
				});
	}

	@Test
	public void customCacheResolverCanBeDefined() throws Exception {
		this.context.withUserConfiguration(SpecificCacheResolverConfiguration.class)
				.withPropertyValues("spring.cache.type=simple").run((loaded) -> {
					getCacheManager(loaded, ConcurrentMapCacheManager.class);
					assertThat(loaded).getBeans(CacheResolver.class).hasSize(1);
				});
	}

	@Test
	public void notSupportedCachingMode() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=foobar").run((loaded) -> {
					assertThat(loaded).getFailure()
							.isInstanceOf(BeanCreationException.class)
							.hasMessageContaining(
									"Failed to bind properties under 'spring.cache.type'");
				});
	}

	@Test
	public void simpleCacheExplicit() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=simple").run((loaded) -> {
					assertThat(getCacheManager(loaded, ConcurrentMapCacheManager.class)
							.getCacheNames()).isEmpty();
				});
	}

	@Test
	public void simpleCacheWithCustomizers() {
		this.context.withUserConfiguration(DefaultCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "simple")
				.run(dunno("allCacheManagerCustomizer", "simpleCacheManagerCustomizer"));
	}

	@Test
	public void simpleCacheExplicitWithCacheNames() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=simple",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					ConcurrentMapCacheManager cacheManager = getCacheManager(loaded,
							ConcurrentMapCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void genericCacheWithCaches() {
		this.context.withUserConfiguration(GenericCacheConfiguration.class)
				.run((loaded) -> {
					SimpleCacheManager cacheManager = getCacheManager(loaded,
							SimpleCacheManager.class);
					assertThat(cacheManager.getCache("first"))
							.isEqualTo(loaded.getBean("firstCache"));
					assertThat(cacheManager.getCache("second"))
							.isEqualTo(loaded.getBean("secondCache"));
					assertThat(cacheManager.getCacheNames()).hasSize(2);
				});
	}

	@Test
	public void genericCacheExplicit() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=generic").run((loaded) -> {
					assertThat(loaded).getFailure()
							.isInstanceOf(BeanCreationException.class)
							.hasMessageContaining(
									"No cache manager could be auto-configured")
							.hasMessageContaining("GENERIC");
				});
	}

	@Test
	public void genericCacheWithCustomizers() {
		this.context.withUserConfiguration(GenericCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "generic")
				.run(dunno("allCacheManagerCustomizer", "genericCacheManagerCustomizer"));
	}

	@Test
	public void genericCacheExplicitWithCaches() {
		this.context.withUserConfiguration(GenericCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=generic").run((loaded) -> {
					SimpleCacheManager cacheManager = getCacheManager(loaded,
							SimpleCacheManager.class);
					assertThat(cacheManager.getCache("first"))
							.isEqualTo(loaded.getBean("firstCache"));
					assertThat(cacheManager.getCache("second"))
							.isEqualTo(loaded.getBean("secondCache"));
					assertThat(cacheManager.getCacheNames()).hasSize(2);
				});
	}

	@Test
	public void couchbaseCacheExplicit() {
		this.context.withUserConfiguration(CouchbaseCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=couchbase").run((loaded) -> {
					CouchbaseCacheManager cacheManager = getCacheManager(loaded,
							CouchbaseCacheManager.class);
					assertThat(cacheManager.getCacheNames()).isEmpty();
				});
	}

	@Test
	public void couchbaseCacheWithCustomizers() {
		this.context
				.withUserConfiguration(CouchbaseCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "couchbase").run(dunno(
						"allCacheManagerCustomizer", "couchbaseCacheManagerCustomizer"));
	}

	@Test
	public void couchbaseCacheExplicitWithCaches() {
		this.context.withUserConfiguration(CouchbaseCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=couchbase",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					CouchbaseCacheManager cacheManager = getCacheManager(loaded,
							CouchbaseCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
					Cache cache = cacheManager.getCache("foo");
					assertThat(cache).isInstanceOf(CouchbaseCache.class);
					assertThat(((CouchbaseCache) cache).getTtl()).isEqualTo(0);
					assertThat(((CouchbaseCache) cache).getNativeCache())
							.isEqualTo(loaded.getBean("bucket"));
				});
	}

	@Test
	public void couchbaseCacheExplicitWithTtl() {
		this.context.withUserConfiguration(CouchbaseCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=couchbase",
						"spring.cache.cacheNames=foo,bar",
						"spring.cache.couchbase.expiration=2000")
				.run((loaded) -> {
					CouchbaseCacheManager cacheManager = getCacheManager(loaded,
							CouchbaseCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
					Cache cache = cacheManager.getCache("foo");
					assertThat(cache).isInstanceOf(CouchbaseCache.class);
					assertThat(((CouchbaseCache) cache).getTtl()).isEqualTo(2);
					assertThat(((CouchbaseCache) cache).getNativeCache())
							.isEqualTo(loaded.getBean("bucket"));
				});
	}

	@Test
	public void redisCacheExplicit() {
		this.context.withUserConfiguration(RedisCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=redis").run((loaded) -> {
					RedisCacheManager cacheManager = getCacheManager(loaded,
							RedisCacheManager.class);
					assertThat(cacheManager.getCacheNames()).isEmpty();
					assertThat(
							((org.springframework.data.redis.cache.RedisCacheConfiguration) new DirectFieldAccessor(
									cacheManager).getPropertyValue("defaultCacheConfig"))
											.usePrefix()).isTrue();
				});
	}

	@Test
	public void redisCacheWithCustomizers() {
		this.context.withUserConfiguration(RedisCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "redis")
				.run(dunno("allCacheManagerCustomizer", "redisCacheManagerCustomizer"));
	}

	@Test
	public void redisCacheExplicitWithCaches() {
		this.context.withUserConfiguration(RedisCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=redis",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					RedisCacheManager cacheManager = getCacheManager(loaded,
							RedisCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void noOpCacheExplicit() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=none").run((loaded) -> {
					NoOpCacheManager cacheManager = getCacheManager(loaded,
							NoOpCacheManager.class);
					assertThat(cacheManager.getCacheNames()).isEmpty();
				});
	}

	@Test
	public void jCacheCacheNoProviderExplicit() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache").run((loaded) -> {
					assertThat(loaded).getFailure()
							.isInstanceOf(BeanCreationException.class)
							.hasMessageContaining(
									"No cache manager could be auto-configured")
							.hasMessageContaining("JCACHE");
				});
	}

	@Test
	public void jCacheCacheWithProvider() {
		String cachingProviderFqn = MockCachingProvider.class.getName();
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn)
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					assertThat(cacheManager.getCacheNames()).isEmpty();
					assertThat(loaded.getBean(javax.cache.CacheManager.class))
							.isEqualTo(cacheManager.getCacheManager());
				});
	}

	@Test
	public void jCacheCacheWithCaches() {
		String cachingProviderFqn = MockCachingProvider.class.getName();
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn,
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void jCacheCacheWithCachesAndCustomConfig() {
		String cachingProviderFqn = MockCachingProvider.class.getName();
		this.context.withUserConfiguration(JCacheCustomConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn,
						"spring.cache.cacheNames[0]=one",
						"spring.cache.cacheNames[1]=two")
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("one", "two");
					CompleteConfiguration<?, ?> defaultCacheConfiguration = loaded
							.getBean(CompleteConfiguration.class);
					verify(cacheManager.getCacheManager()).createCache("one",
							defaultCacheConfiguration);
					verify(cacheManager.getCacheManager()).createCache("two",
							defaultCacheConfiguration);
				});
	}

	@Test
	public void jCacheCacheWithExistingJCacheManager() {
		this.context.withUserConfiguration(JCacheCustomCacheManager.class)
				.withPropertyValues("spring.cache.type=jcache").run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					assertThat(cacheManager.getCacheManager())
							.isEqualTo(loaded.getBean("customJCacheCacheManager"));
				});
	}

	@Test
	public void jCacheCacheWithUnknownProvider() {
		String wrongCachingProviderClassName = "org.acme.FooBar";
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + wrongCachingProviderClassName)
				.run((loaded) -> {
					assertThat(loaded).getFailure()
							.isInstanceOf(BeanCreationException.class)
							.hasMessageContaining(wrongCachingProviderClassName);
				});
	}

	@Test
	public void jCacheCacheWithConfig() {
		String cachingProviderFqn = MockCachingProvider.class.getName();
		String configLocation = "org/springframework/boot/autoconfigure/hazelcast/hazelcast-specific.xml";
		this.context.withUserConfiguration(JCacheCustomConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn,
						"spring.cache.jcache.config=" + configLocation)
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					Resource configResource = new ClassPathResource(configLocation);
					assertThat(cacheManager.getCacheManager().getURI())
							.isEqualTo(configResource.getURI());
				});
	}

	@Test
	public void jCacheCacheWithWrongConfig() {
		String cachingProviderFqn = MockCachingProvider.class.getName();
		String configLocation = "org/springframework/boot/autoconfigure/cache/does-not-exist.xml";
		this.context.withUserConfiguration(JCacheCustomConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn,
						"spring.cache.jcache.config=" + configLocation)
				.run((loaded) -> {
					assertThat(loaded).getFailure()
							.isInstanceOf(BeanCreationException.class)
							.hasMessageContaining("does not exist")
							.hasMessageContaining(configLocation);
				});
	}

	@Test
	public void ehcacheCacheWithCaches() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=ehcache").run((loaded) -> {
					EhCacheCacheManager cacheManager = getCacheManager(loaded,
							EhCacheCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("cacheTest1",
							"cacheTest2");
					assertThat(loaded.getBean(net.sf.ehcache.CacheManager.class))
							.isEqualTo(cacheManager.getCacheManager());
				});
	}

	@Test
	public void ehcacheCacheWithCustomizers() {
		this.context.withUserConfiguration(DefaultCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "ehcache")
				.run(dunno("allCacheManagerCustomizer", "ehcacheCacheManagerCustomizer"));
	}

	@Test
	public void ehcacheCacheWithConfig() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=ehcache",
						"spring.cache.ehcache.config=cache/ehcache-override.xml")
				.run((loaded) -> {
					EhCacheCacheManager cacheManager = getCacheManager(loaded,
							EhCacheCacheManager.class);
					assertThat(cacheManager.getCacheNames())
							.containsOnly("cacheOverrideTest1", "cacheOverrideTest2");
				});
	}

	@Test
	public void ehcacheCacheWithExistingCacheManager() {
		this.context.withUserConfiguration(EhCacheCustomCacheManager.class)
				.withPropertyValues("spring.cache.type=ehcache").run((loaded) -> {
					EhCacheCacheManager cacheManager = getCacheManager(loaded,
							EhCacheCacheManager.class);
					assertThat(cacheManager.getCacheManager())
							.isEqualTo(loaded.getBean("customEhCacheCacheManager"));
				});
	}

	@Test
	public void ehcache3AsJCacheWithCaches() {
		String cachingProviderFqn = EhcacheCachingProvider.class.getName();
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn,
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void ehcache3AsJCacheWithConfig() throws IOException {
		String cachingProviderFqn = EhcacheCachingProvider.class.getName();
		String configLocation = "ehcache3.xml";
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn,
						"spring.cache.jcache.config=" + configLocation)
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);

					Resource configResource = new ClassPathResource(configLocation);
					assertThat(cacheManager.getCacheManager().getURI())
							.isEqualTo(configResource.getURI());
					assertThat(cacheManager.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void hazelcastCacheExplicit() {
		this.context
				.withConfiguration(
						AutoConfigurations.of(HazelcastAutoConfiguration.class))
				.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=hazelcast").run((loaded) -> {
					HazelcastCacheManager cacheManager = getCacheManager(loaded,
							HazelcastCacheManager.class);
					// NOTE: the hazelcast implementation knows about a cache in a lazy
					// manner.
					cacheManager.getCache("defaultCache");
					assertThat(cacheManager.getCacheNames()).containsOnly("defaultCache");
					assertThat(loaded.getBean(HazelcastInstance.class))
							.isEqualTo(cacheManager.getHazelcastInstance());
				});
	}

	@Test
	public void hazelcastCacheWithCustomizers() {
		this.context
				.withUserConfiguration(HazelcastCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "hazelcast").run(dunno(
						"allCacheManagerCustomizer", "hazelcastCacheManagerCustomizer"));
	}

	@Test
	public void hazelcastCacheWithExistingHazelcastInstance() {
		this.context.withUserConfiguration(HazelcastCustomHazelcastInstance.class)
				.withPropertyValues("spring.cache.type=hazelcast").run((loaded) -> {
					HazelcastCacheManager cacheManager = getCacheManager(loaded,
							HazelcastCacheManager.class);
					assertThat(cacheManager.getHazelcastInstance())
							.isEqualTo(loaded.getBean("customHazelcastInstance"));
				});
	}

	@Test
	public void hazelcastCacheWithHazelcastAutoConfiguration() throws IOException {
		String hazelcastConfig = "org/springframework/boot/autoconfigure/hazelcast/hazelcast-specific.xml";
		this.context
				.withConfiguration(
						AutoConfigurations.of(HazelcastAutoConfiguration.class))
				.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=hazelcast",
						"spring.hazelcast.config=" + hazelcastConfig)
				.run((loaded) -> {
					HazelcastCacheManager cacheManager = getCacheManager(loaded,
							HazelcastCacheManager.class);
					HazelcastInstance hazelcastInstance = loaded
							.getBean(HazelcastInstance.class);
					assertThat(cacheManager.getHazelcastInstance())
							.isSameAs(hazelcastInstance);
					assertThat(hazelcastInstance.getConfig().getConfigurationFile())
							.isEqualTo(new ClassPathResource(hazelcastConfig).getFile());
					assertThat(cacheManager.getCache("foobar")).isNotNull();
					assertThat(cacheManager.getCacheNames()).containsOnly("foobar");
				});
	}

	@Test
	public void hazelcastAsJCacheWithCaches() {
		String cachingProviderFqn = HazelcastCachingProvider.class.getName();
		try {
			this.context.withUserConfiguration(DefaultCacheConfiguration.class)
					.withPropertyValues("spring.cache.type=jcache",
							"spring.cache.jcache.provider=" + cachingProviderFqn,
							"spring.cache.cacheNames[0]=foo",
							"spring.cache.cacheNames[1]=bar")
					.run((loaded) -> {
						JCacheCacheManager cacheManager = getCacheManager(loaded,
								JCacheCacheManager.class);
						assertThat(cacheManager.getCacheNames()).containsOnly("foo",
								"bar");
						assertThat(Hazelcast.getAllHazelcastInstances()).hasSize(1);
					});
		}
		finally {
			Caching.getCachingProvider(cachingProviderFqn).close();
		}
	}

	@Test
	public void hazelcastAsJCacheWithConfig() throws IOException {
		String cachingProviderFqn = HazelcastCachingProvider.class.getName();
		try {
			String configLocation = "org/springframework/boot/autoconfigure/hazelcast/hazelcast-specific.xml";
			this.context.withUserConfiguration(DefaultCacheConfiguration.class)
					.withPropertyValues("spring.cache.type=jcache",
							"spring.cache.jcache.provider=" + cachingProviderFqn,
							"spring.cache.jcache.config=" + configLocation)
					.run((loaded) -> {
						JCacheCacheManager cacheManager = getCacheManager(loaded,
								JCacheCacheManager.class);
						Resource configResource = new ClassPathResource(configLocation);
						assertThat(cacheManager.getCacheManager().getURI())
								.isEqualTo(configResource.getURI());
						assertThat(Hazelcast.getAllHazelcastInstances()).hasSize(1);
					});
		}
		finally {
			Caching.getCachingProvider(cachingProviderFqn).close();
		}
	}

	@Test
	public void hazelcastAsJCacheWithExistingHazelcastInstance() throws IOException {
		String cachingProviderFqn = HazelcastCachingProvider.class.getName();
		this.context
				.withConfiguration(
						AutoConfigurations.of(HazelcastAutoConfiguration.class))
				.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderFqn)
				.run((loaded) -> {
					JCacheCacheManager cacheManager = getCacheManager(loaded,
							JCacheCacheManager.class);
					javax.cache.CacheManager jCacheManager = cacheManager
							.getCacheManager();
					assertThat(jCacheManager).isInstanceOf(
							com.hazelcast.cache.HazelcastCacheManager.class);
					assertThat(loaded.getBeansOfType(HazelcastInstance.class)).hasSize(1);
					HazelcastInstance hazelcastInstance = loaded
							.getBean(HazelcastInstance.class);
					assertThat(((com.hazelcast.cache.HazelcastCacheManager) jCacheManager)
							.getHazelcastInstance()).isSameAs(hazelcastInstance);
					assertThat(hazelcastInstance.getName()).isEqualTo("default-instance");
					assertThat(Hazelcast.getAllHazelcastInstances()).hasSize(1);
				});
	}

	@Test
	public void infinispanCacheWithConfig() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=infinispan",
						"spring.cache.infinispan.config=infinispan.xml")
				.run((loaded) -> {
					SpringEmbeddedCacheManager cacheManager = getCacheManager(loaded,
							SpringEmbeddedCacheManager.class);
					assertThat(cacheManager.getCacheNames()).contains("foo", "bar");
				});
	}

	@Test
	public void infinispanCacheWithCustomizers() {
		this.context.withUserConfiguration(DefaultCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "infinispan").run(dunno(
						"allCacheManagerCustomizer", "infinispanCacheManagerCustomizer"));
	}

	@Test
	public void infinispanCacheWithCaches() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=infinispan",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					assertThat(getCacheManager(loaded, SpringEmbeddedCacheManager.class)
							.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void infinispanCacheWithCachesAndCustomConfig() {
		this.context.withUserConfiguration(InfinispanCustomConfiguration.class)
				.withPropertyValues("spring.cache.type=infinispan",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					assertThat(getCacheManager(loaded, SpringEmbeddedCacheManager.class)
							.getCacheNames()).containsOnly("foo", "bar");
					verify(loaded.getBean(ConfigurationBuilder.class), times(2)).build();
				});
	}

	@Test
	public void infinispanAsJCacheWithCaches() {
		String cachingProviderClassName = JCachingProvider.class.getName();
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderClassName,
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run((loaded) -> {
					assertThat(getCacheManager(loaded, JCacheCacheManager.class)
							.getCacheNames()).containsOnly("foo", "bar");
				});
	}

	@Test
	public void infinispanAsJCacheWithConfig() throws IOException {
		String cachingProviderClassName = JCachingProvider.class.getName();
		String configLocation = "infinispan.xml";
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=jcache",
						"spring.cache.jcache.provider=" + cachingProviderClassName,
						"spring.cache.jcache.config=" + configLocation)
				.run((loaded) -> {
					Resource configResource = new ClassPathResource(configLocation);
					assertThat(getCacheManager(loaded, JCacheCacheManager.class)
							.getCacheManager().getURI())
									.isEqualTo(configResource.getURI());
				});
	}

	@Test
	public void jCacheCacheWithCachesAndCustomizer() {
		String cachingProviderClassName = HazelcastCachingProvider.class.getName();
		try {
			this.context.withUserConfiguration(JCacheWithCustomizerConfiguration.class)
					.withPropertyValues("spring.cache.type=jcache",
							"spring.cache.jcache.provider=" + cachingProviderClassName,
							"spring.cache.cacheNames[0]=foo",
							"spring.cache.cacheNames[1]=bar")
					.run((loaded) -> {
						// see customizer
						assertThat(getCacheManager(loaded, JCacheCacheManager.class)
								.getCacheNames()).containsOnly("foo", "custom1");
					});
		}
		finally {
			Caching.getCachingProvider(cachingProviderClassName).close();
		}
	}

	@Test
	public void caffeineCacheWithExplicitCaches() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=caffeine",
						"spring.cache.cacheNames=foo")
				.run((loaded) -> {
					CaffeineCacheManager manager = getCacheManager(loaded,
							CaffeineCacheManager.class);
					assertThat(manager.getCacheNames()).containsOnly("foo");
					Cache foo = manager.getCache("foo");
					foo.get("1");
					// See next tests: no spec given so stats should be disabled
					assertThat(((CaffeineCache) foo).getNativeCache().stats().missCount())
							.isEqualTo(0L);
				});
	}

	@Test
	public void caffeineCacheWithCustomizers() {
		this.context.withUserConfiguration(DefaultCacheAndCustomizersConfiguration.class)
				.withPropertyValues("spring.cache.type=" + "caffeine").run(dunno(
						"allCacheManagerCustomizer", "caffeineCacheManagerCustomizer"));
	}

	@Test
	public void caffeineCacheWithExplicitCacheBuilder() {
		this.context.withUserConfiguration(CaffeineCacheBuilderConfiguration.class)
				.withPropertyValues("spring.cache.type=caffeine",
						"spring.cache.cacheNames=foo,bar")
				.run(this::validateCaffeineCacheWithStats);
	}

	@Test
	public void caffeineCacheExplicitWithSpec() {
		this.context.withUserConfiguration(CaffeineCacheSpecConfiguration.class)
				.withPropertyValues("spring.cache.type=caffeine",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run(this::validateCaffeineCacheWithStats);
	}

	@Test
	public void caffeineCacheExplicitWithSpecString() {
		this.context.withUserConfiguration(DefaultCacheConfiguration.class)
				.withPropertyValues("spring.cache.type=caffeine",
						"spring.cache.caffeine.spec=recordStats",
						"spring.cache.cacheNames[0]=foo",
						"spring.cache.cacheNames[1]=bar")
				.run(this::validateCaffeineCacheWithStats);
	}

	private void validateCaffeineCacheWithStats(AssertableApplicationContext context) {
		CaffeineCacheManager manager = getCacheManager(context,
				CaffeineCacheManager.class);
		assertThat(manager.getCacheNames()).containsOnly("foo", "bar");
		Cache foo = manager.getCache("foo");
		foo.get("1");
		assertThat(((CaffeineCache) foo).getNativeCache().stats().missCount())
				.isEqualTo(1L);
	}

	@SuppressWarnings("rawtypes")
	private ContextConsumer<AssertableApplicationContext> dunno(
			String... expectedCustomizerNames) {
		return (loaded) -> {
			CacheManager cacheManager = getCacheManager(loaded, CacheManager.class);
			List<String> expected = new ArrayList<>(
					Arrays.asList(expectedCustomizerNames));
			Map<String, CacheManagerTestCustomizer> customizer = loaded
					.getBeansOfType(CacheManagerTestCustomizer.class);
			customizer.forEach((key, value) -> {
				if (expected.contains(key)) {
					expected.remove(key);
					assertThat(value.cacheManager).isSameAs(cacheManager);
				}
				else {
					assertThat(value.cacheManager).isNull();
				}
			});
			assertThat(expected).hasSize(0);
		};
	}

	private <T extends CacheManager> T getCacheManager(
			AssertableApplicationContext loaded, Class<T> type) {
		CacheManager cacheManager = loaded.getBean(CacheManager.class);
		assertThat(cacheManager).as("Wrong cache manager type").isInstanceOf(type);
		return type.cast(cacheManager);
	}

	@Configuration
	static class EmptyConfiguration {

	}

	@Configuration
	@EnableCaching
	static class DefaultCacheConfiguration {

	}

	@Configuration
	@EnableCaching
	@Import(CacheManagerCustomizersConfiguration.class)
	static class DefaultCacheAndCustomizersConfiguration {

	}

	@Configuration
	@EnableCaching
	static class GenericCacheConfiguration {

		@Bean
		public Cache firstCache() {
			return new ConcurrentMapCache("first");
		}

		@Bean
		public Cache secondCache() {
			return new ConcurrentMapCache("second");
		}

	}

	@Configuration
	@Import({ GenericCacheConfiguration.class,
			CacheManagerCustomizersConfiguration.class })
	static class GenericCacheAndCustomizersConfiguration {

	}

	@Configuration
	@EnableCaching
	@Import({ HazelcastAutoConfiguration.class,
			CacheManagerCustomizersConfiguration.class })
	static class HazelcastCacheAndCustomizersConfiguration {

	}

	@Configuration
	@EnableCaching
	static class CouchbaseCacheConfiguration {

		@Bean
		public Bucket bucket() {
			BucketManager bucketManager = mock(BucketManager.class);
			Bucket bucket = mock(Bucket.class);
			given(bucket.bucketManager()).willReturn(bucketManager);
			return bucket;
		}

	}

	@Configuration
	@Import({ CouchbaseCacheConfiguration.class,
			CacheManagerCustomizersConfiguration.class })
	static class CouchbaseCacheAndCustomizersConfiguration {

	}

	@Configuration
	@EnableCaching
	static class RedisCacheConfiguration {

		@Bean
		public RedisConnectionFactory redisConnectionFactory() {
			return mock(RedisConnectionFactory.class);
		}

	}

	@Configuration
	@Import({ RedisCacheConfiguration.class, CacheManagerCustomizersConfiguration.class })
	static class RedisCacheAndCustomizersConfiguration {

	}

	@Configuration
	@EnableCaching
	static class JCacheCustomConfiguration {

		@Bean
		public CompleteConfiguration<?, ?> defaultCacheConfiguration() {
			return mock(CompleteConfiguration.class);
		}

	}

	@Configuration
	@EnableCaching
	static class JCacheCustomCacheManager {

		@Bean
		public javax.cache.CacheManager customJCacheCacheManager() {
			javax.cache.CacheManager cacheManager = mock(javax.cache.CacheManager.class);
			given(cacheManager.getCacheNames())
					.willReturn(Collections.<String>emptyList());
			return cacheManager;
		}

	}

	@Configuration
	@EnableCaching
	static class JCacheWithCustomizerConfiguration {

		@Bean
		JCacheManagerCustomizer myCustomizer() {
			return new JCacheManagerCustomizer() {
				@Override
				public void customize(javax.cache.CacheManager cacheManager) {
					MutableConfiguration<?, ?> config = new MutableConfiguration<>();
					config.setExpiryPolicyFactory(
							CreatedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));
					config.setStatisticsEnabled(true);
					cacheManager.createCache("custom1", config);
					cacheManager.destroyCache("bar");
				}
			};
		}

	}

	@Configuration
	@EnableCaching
	static class EhCacheCustomCacheManager {

		@Bean
		public net.sf.ehcache.CacheManager customEhCacheCacheManager() {
			net.sf.ehcache.CacheManager cacheManager = mock(
					net.sf.ehcache.CacheManager.class);
			given(cacheManager.getStatus()).willReturn(Status.STATUS_ALIVE);
			given(cacheManager.getCacheNames()).willReturn(new String[0]);
			return cacheManager;
		}

	}

	@Configuration
	@EnableCaching
	static class HazelcastCustomHazelcastInstance {

		@Bean
		public HazelcastInstance customHazelcastInstance() {
			return mock(HazelcastInstance.class);
		}

	}

	@Configuration
	@EnableCaching
	static class InfinispanCustomConfiguration {

		@Bean
		public ConfigurationBuilder configurationBuilder() {
			ConfigurationBuilder builder = mock(ConfigurationBuilder.class);
			given(builder.build()).willReturn(new ConfigurationBuilder().build());
			return builder;
		}

	}

	@Configuration
	@EnableCaching
	static class CustomCacheManagerConfiguration {

		@Bean
		public CacheManager cacheManager() {
			return new ConcurrentMapCacheManager("custom1");
		}

	}

	@Configuration
	@EnableCaching
	static class CustomCacheManagerFromSupportConfiguration
			extends CachingConfigurerSupport {

		@Override
		@Bean
		// The @Bean annotation is important, see CachingConfigurerSupport Javadoc
		public CacheManager cacheManager() {
			return new ConcurrentMapCacheManager("custom1");
		}

	}

	@Configuration
	@EnableCaching
	static class CustomCacheResolverFromSupportConfiguration
			extends CachingConfigurerSupport {

		@Override
		@Bean
		// The @Bean annotation is important, see CachingConfigurerSupport Javadoc
		public CacheResolver cacheResolver() {
			return new CacheResolver() {

				@Override
				public Collection<? extends Cache> resolveCaches(
						CacheOperationInvocationContext<?> context) {
					return Collections.singleton(mock(Cache.class));
				}

			};

		}

	}

	@Configuration
	@EnableCaching
	static class SpecificCacheResolverConfiguration {

		@Bean
		public CacheResolver myCacheResolver() {
			return mock(CacheResolver.class);
		}

	}

	@Configuration
	@EnableCaching
	static class CaffeineCacheBuilderConfiguration {

		@Bean
		Caffeine<Object, Object> cacheBuilder() {
			return Caffeine.newBuilder().recordStats();
		}

	}

	@Configuration
	@EnableCaching
	static class CaffeineCacheSpecConfiguration {

		@Bean
		CaffeineSpec caffeineSpec() {
			return CaffeineSpec.parse("recordStats");
		}

	}

	@Configuration
	static class CacheManagerCustomizersConfiguration {

		@Bean
		public CacheManagerCustomizer<CacheManager> allCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<CacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<ConcurrentMapCacheManager> simpleCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<ConcurrentMapCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<SimpleCacheManager> genericCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<SimpleCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<CouchbaseCacheManager> couchbaseCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<CouchbaseCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<RedisCacheManager> redisCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<RedisCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<EhCacheCacheManager> ehcacheCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<EhCacheCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<HazelcastCacheManager> hazelcastCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<HazelcastCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<SpringEmbeddedCacheManager> infinispanCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<SpringEmbeddedCacheManager>() {

			};
		}

		@Bean
		public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
			return new CacheManagerTestCustomizer<CaffeineCacheManager>() {

			};
		}

	}

	static abstract class CacheManagerTestCustomizer<T extends CacheManager>
			implements CacheManagerCustomizer<T> {

		private T cacheManager;

		@Override
		public void customize(T cacheManager) {
			if (this.cacheManager != null) {
				throw new IllegalStateException("Customized invoked twice");
			}
			this.cacheManager = cacheManager;
		}

	}

}
