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

package org.springframework.boot.autoconfigure.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testsupport.runner.classpath.ClassPathExclusions;
import org.springframework.boot.testsupport.runner.classpath.ModifiedClassPathRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HazelcastAutoConfiguration} with full classpath.
 *
 * @author Stephane Nicoll
 * @author Neil Stevenson
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions("hazelcast-jet-*.jar")
public class HazelcastAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(HazelcastAutoConfiguration.class));

	@Test
	public void defaultConfigFile() {
		// no hazelcast-client.xml and hazelcast.xml is present in root classpath
		this.contextRunner.run((context) -> {
			Config config = context.getBean(HazelcastInstance.class).getConfig();
			assertThat(config.getConfigurationUrl())
					.isEqualTo(new ClassPathResource("hazelcast.xml").getURL());
		});
	}

}
