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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.serviceregistry.endpoint.ServiceRegistryEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务注册EndPoint，ServiceRegistryEndpoint主要用于设置和获取服务实例的状态值
 *
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
public class ServiceRegistryAutoConfiguration {

	@ConditionalOnBean(ServiceRegistry.class)
	@ConditionalOnClass(Endpoint.class)
	protected class ServiceRegistryEndpointConfiguration {

		//当前上下文的服务实例
		@Autowired(required = false)
		private Registration registration;

		@Bean
		@ConditionalOnAvailableEndpoint
		public ServiceRegistryEndpoint serviceRegistryEndpoint(ServiceRegistry serviceRegistry) {
			ServiceRegistryEndpoint endpoint = new ServiceRegistryEndpoint(serviceRegistry);
			endpoint.setRegistration(this.registration);
			return endpoint;
		}

	}

}
