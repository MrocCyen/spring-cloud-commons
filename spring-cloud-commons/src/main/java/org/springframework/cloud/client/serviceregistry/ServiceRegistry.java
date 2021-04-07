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

package org.springframework.cloud.client.serviceregistry;

/**
 * Contract to register and deregister instances with a Service Registry.
 *
 * @param <R> registration meta data
 * @author Spencer Gibb
 * @since 1.2.0
 */

/**
 * 服务注册
 *
 * @param <R> 服务实例，但是用的是服务实例接口的子接口，Registration是一个标记接口
 */
public interface ServiceRegistry<R extends Registration> {

	/**
	 * Registers the registration. A registration typically has information about an
	 * instance, such as its hostname and port.
	 *
	 * @param registration registration meta data
	 */
	//注册服务实例
	void register(R registration);

	/**
	 * Deregisters the registration.
	 *
	 * @param registration registration meta data
	 */
	//注销服务实例
	void deregister(R registration);

	/**
	 * Closes the ServiceRegistry. This is a lifecycle method.
	 */
	//关闭当前服务注册
	void close();

	/**
	 * Sets the status of the registration. The status values are determined by the
	 * individual implementations.
	 *
	 * @param registration The registration to update.
	 * @param status       The status to set.
	 * @see org.springframework.cloud.client.serviceregistry.endpoint.ServiceRegistryEndpoint
	 */
	//设置当前服务实例的状态
	void setStatus(R registration, String status);

	/**
	 * Gets the status of a particular registration.
	 *
	 * @param registration The registration to query.
	 * @param <T>          The type of the status.
	 * @return The status of the registration.
	 * @see org.springframework.cloud.client.serviceregistry.endpoint.ServiceRegistryEndpoint
	 */
	//获取当前服务实例的状态
	<T> T getStatus(R registration);

}
