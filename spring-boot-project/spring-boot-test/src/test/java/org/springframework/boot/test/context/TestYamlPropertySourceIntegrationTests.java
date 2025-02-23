/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.test.context;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link YamlPropertySourceFactory} with
 * {@link TestYamlPropertySource}.
 *
 * @author Dmytro Nosan
 */
@SpringJUnitConfig
@TestYamlPropertySource({ "test.yaml", "test1.yaml" })
@TestYamlPropertySource(locations = "test2.yaml", properties = "key:value")
class TestYamlPropertySourceIntegrationTests {

	@Autowired
	private Environment environment;

	@Test
	void loadProperties() {
		assertThat(this.environment.getProperty("spring.bar")).isEqualTo("bar");
		assertThat(this.environment.getProperty("spring.foo")).isEqualTo("baz");
		assertThat(this.environment.getProperty("spring.buzz")).isEqualTo("fazz");
		assertThat(this.environment.getProperty("spring.boot")).isEqualTo("boot");
		assertThat(this.environment.getProperty("key")).isEqualTo("value");
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

	}

}
