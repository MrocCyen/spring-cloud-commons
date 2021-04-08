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

package org.springframework.cloud.client;

import java.net.URI;
import java.util.Map;

/**
 * Represents an instance of a service in a discovery system.
 * </p>
 * 表示一个服务实例
 * 一个服务可能有多个实例，所以会存在多个实例id，但是这几个实例对应的服务id只有一个
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 */
public interface ServiceInstance {

	/**
	 * @return The unique instance ID as registered.
	 */
	/**
	 * 服务实例id
	 *
	 * @return
	 */
	default String getInstanceId() {
		return null;
	}

	/**
	 * @return The service ID as registered.
	 */
	/**
	 * 服务id
	 *
	 * @return
	 */
	String getServiceId();

	/**
	 * @return The hostname of the registered service instance.
	 */
	/**
	 * 主机
	 *
	 * @return
	 */
	String getHost();

	/**
	 * @return The port of the registered service instance.
	 */
	/**
	 * 端口
	 *
	 * @return
	 */
	int getPort();

	/**
	 * @return Whether the port of the registered service instance uses HTTPS.
	 */
	/**
	 * 是否是https
	 *
	 * @return
	 */
	boolean isSecure();

	/**
	 * @return The service URI address.
	 */
	/**
	 * 服务uri
	 *
	 * @return
	 */
	URI getUri();

	/**
	 * @return The key / value pair metadata associated with the service instance.
	 */
	/**
	 * 服务元数据
	 *
	 * @return
	 */
	Map<String, String> getMetadata();

	/**
	 * @return The scheme of the service instance.
	 */
	/**
	 * 服务实例的scheme
	 *
	 * @return
	 */
	default String getScheme() {
		return null;
	}

}
