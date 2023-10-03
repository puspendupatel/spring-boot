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

package org.springframework.boot.loader;

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.loader.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LaunchedURLClassLoader}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
@SuppressWarnings("resource")
class LaunchedURLClassLoaderTests {

	@TempDir
	File tempDir;

	@Test
	void resolveResourceFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResource("demo/Application.java")).isNotNull();
	}

	@Test
	void resolveResourcesFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResources("demo/Application.java").hasMoreElements()).isTrue();
	}

	@Test
	void resolveRootPathFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResource("")).isNotNull();
	}

	@Test
	void resolveRootResourcesFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResources("").hasMoreElements()).isTrue();
	}

	@Test
	void resolveFromNested() throws Exception {
		File file = new File(this.tempDir, "test.jar");
		TestJarCreator.createTestJar(file);
		try (JarFile jarFile = new JarFile(file)) {
			URL url = jarFile.getUrl();
			try (LaunchedURLClassLoader loader = new LaunchedURLClassLoader(new URL[] { url }, null)) {
				URL resource = loader.getResource("nested.jar!/3.dat");
				assertThat(resource).hasToString(url + "nested.jar!/3.dat");
				try (InputStream input = resource.openConnection().getInputStream()) {
					assertThat(input.read()).isEqualTo(3);
				}
			}
		}
	}

	@Test
	void resolveFromNestedWhileThreadIsInterrupted() throws Exception {
		File file = new File(this.tempDir, "test.jar");
		TestJarCreator.createTestJar(file);
		try (JarFile jarFile = new JarFile(file)) {
			URL url = jarFile.getUrl();
			try (LaunchedURLClassLoader loader = new LaunchedURLClassLoader(new URL[] { url }, null)) {
				Thread.currentThread().interrupt();
				URL resource = loader.getResource("nested.jar!/3.dat");
				assertThat(resource).hasToString(url + "nested.jar!/3.dat");
				URLConnection connection = resource.openConnection();
				try (InputStream input = connection.getInputStream()) {
					assertThat(input.read()).isEqualTo(3);
				}
				((JarURLConnection) connection).getJarFile().close();
			}
			finally {
				Thread.interrupted();
			}
		}
	}

	@Test
	void enableLoaderCache() throws Exception {
		resolveResourceFromArchive();
		resolveResourcesFromArchive();
		resolveRootPathFromArchive();
		resolveRootResourcesFromArchive();
		resolveFromNested();
		resolveFromNestedWhileThreadIsInterrupted();

		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		loader.setEnableCache(true);
		assertThat(loader.getResource("demo/Application.java")).isEqualTo(loader.getResource("demo/Application.java"));
		assertThat(loader.loadClass("demo.Application")).isEqualTo(loader.loadClass("demo.Application"));
		assertThat(loader.getResource("demo/ApplicationNotExist.java")).isNull();
		assertThat(loader.getResources("demo/ApplicationNotExist.java").hasMoreElements()).isNotEqualTo(true);
		assertThat(loader.getResources("demo/ApplicationNotExist.java").hasMoreElements()).isNotEqualTo(true);
		ClassNotFoundException ex = null;
		ClassNotFoundException ex1 = null;
		ClassNotFoundException ex2 = null;
		ClassNotFoundException ex3 = null;
		try {
			loader.loadClass("demo.ApplicationNotExist");
		}
		catch (ClassNotFoundException exception) {
			ex = exception;
		}
		try {
			loader.loadClass("demo.ApplicationNotExist");
		}
		catch (ClassNotFoundException exception) {
			ex1 = exception;
		}
		try {
			loader.setEnableCache(false);
			loader.loadClass("demo.ApplicationNotExist");
		}
		catch (ClassNotFoundException exception) {
			ex2 = exception;
			loader.setEnableCache(true);
		}
		try {
			loader.clearCache();
			loader.loadClass("demo.ApplicationNotExist");
		}
		catch (ClassNotFoundException exception) {
			ex3 = exception;
		}
		assertThat(ex).isNotNull();
		assertThat(ex1).isNotNull().isEqualTo(ex);
		assertThat(ex2).isNotNull().isNotEqualTo(ex).isNotEqualTo(ex1);
		assertThat(ex3).isNotNull().isNotEqualTo(ex2).isNotEqualTo(ex1).isNotEqualTo(ex);
	}

}
