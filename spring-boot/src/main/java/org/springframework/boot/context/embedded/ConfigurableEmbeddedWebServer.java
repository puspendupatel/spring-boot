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

package org.springframework.boot.context.embedded;

import java.net.InetAddress;
import java.util.Set;

import org.springframework.boot.web.servlet.ErrorPage;
import org.springframework.boot.web.servlet.ErrorPageRegistry;

/**
 * Interface that regroups common customizations to
 * embedded server factories such as {@link EmbeddedServletContainerFactory}
 * and {@link ReactiveWebServerFactory}.
 * @author Brian Clozel
 * @since 2.0.0
 */
public interface ConfigurableEmbeddedWebServer extends ErrorPageRegistry {

	/**
	 * Sets the port that the embedded servlet container should listen on. If not
	 * specified port '8080' will be used. Use port -1 to disable auto-start (i.e start
	 * the web application context but not have it listen to any port).
	 * @param port the port to set
	 */
	void setPort(int port);

	/**
	 * Sets the specific network address that the server should bind to.
	 * @param address the address to set (defaults to {@code null})
	 */
	void setAddress(InetAddress address);

	/**
	 * Sets the error pages that will be used when handling exceptions.
	 * @param errorPages the error pages
	 */
	void setErrorPages(Set<? extends ErrorPage> errorPages);

	/**
	 * Sets the SSL configuration that will be applied to the container's default
	 * connector.
	 * @param ssl the SSL configuration
	 */
	void setSsl(Ssl ssl);

	/**
	 * Sets a provider that will be used to obtain SSL stores.
	 * @param sslStoreProvider the SSL store provider
	 */
	void setSslStoreProvider(SslStoreProvider sslStoreProvider);

	/**
	 * Sets the compression configuration that will be applied to the container's default
	 * connector.
	 * @param compression the compression configuration
	 */
	void setCompression(Compression compression);

	/**
	 * Sets the server header value.
	 * @param serverHeader the server header value
	 */
	void setServerHeader(String serverHeader);

}
