/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.launchscript;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Condition;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.ansi.AnsiColor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Basic abstract class for testing the launch script. It contains common methods to test
 * the launch script.
 *
 * @author Alexey Vinogradov (vinogradov.a.i.93@gmail.com)
 */
abstract class BasicLaunchScriptIntegrationTests {

	protected static final char ESC = 27;

	static List<Object[]> parameters() {
		List<Object[]> parameters = new ArrayList<>();
		for (File os : new File("src/intTest/resources/conf").listFiles()) {
			for (File version : os.listFiles()) {
				parameters.add(new Object[] { os.getName(), version.getName() });
			}
		}
		return parameters;
	}

	protected Condition<String> coloredString(AnsiColor color, String string) {
		String colorString = ESC + "[0;" + color + "m" + string + ESC + "[0m";
		return new Condition<String>() {

			@Override
			public boolean matches(String value) {
				return containsString(colorString).matches(value);
			}

		};
	}

	protected void doLaunch(String os, String version, String script) throws Exception {
		assertThat(doTest(os, version, script)).contains("Launched");
	}

	protected String doTest(String os, String version, String script) throws Exception {
		ToStringConsumer consumer = new ToStringConsumer().withRemoveAnsiCodes(false);
		try (LaunchScriptTestContainer container = new LaunchScriptTestContainer(os, version, script)) {
			container.withLogConsumer(consumer);
			container.start();
			while (container.isRunning()) {
				Thread.sleep(100);
			}
		}
		return consumer.toUtf8String();
	}

	private static final class LaunchScriptTestContainer extends GenericContainer<LaunchScriptTestContainer> {

		private LaunchScriptTestContainer(String os, String version, String testScript) {
			super(new ImageFromDockerfile("spring-boot-launch-script/" + os.toLowerCase() + "-" + version)
					.withFileFromFile("Dockerfile",
							new File("src/intTest/resources/conf/" + os + "/" + version + "/Dockerfile"))
					.withFileFromFile("app.jar", findApplication())
					.withFileFromFile("test-functions.sh", new File("src/intTest/resources/scripts/test-functions.sh"))
					.withFileFromFile("jar/test-functions.sh",
							new File("src/intTest/resources/scripts/jar/test-functions.sh"))
					.withFileFromFile("init.d/test-functions.sh",
							new File("src/intTest/resources/scripts/init.d/test-functions.sh")));
			withCopyFileToContainer(MountableFile.forHostPath("src/intTest/resources/scripts/" + testScript),
					"/" + testScript);
			withCommand("/bin/bash", "-c", "chmod +x " + testScript + " && ./" + testScript);
			withStartupTimeout(Duration.ofMinutes(10));
		}

		private static File findApplication() {
			File appJar = new File("build/app/build/libs/app.jar");
			if (appJar.isFile()) {
				return appJar;
			}
			throw new IllegalStateException(
					"Could not find test application in build/app/build/libs directory. Have you built it?");
		}

	}

}
