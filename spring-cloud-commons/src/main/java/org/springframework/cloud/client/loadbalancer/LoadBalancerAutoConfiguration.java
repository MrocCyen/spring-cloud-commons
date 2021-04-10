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

package org.springframework.cloud.client.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for blocking client-side load balancing.
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Will Tran
 * @author Gang Li
 * @author Olga Maciaszek-Sharma
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(LoadBalancerClient.class)
@EnableConfigurationProperties(LoadBalancerProperties.class)
public class LoadBalancerAutoConfiguration {

	/**
	 * 注入RestTemplate
	 */
	@LoadBalanced
	@Autowired(required = false)
	private List<RestTemplate> restTemplates = Collections.emptyList();

	/**
	 * 注入负载均衡转换器
	 */
	@Autowired(required = false)
	private List<LoadBalancerRequestTransformer> transformers = Collections.emptyList();

	/**
	 * 针对每一个RestTemplate进行自定义处理
	 */
	@Bean
	public SmartInitializingSingleton loadBalancedRestTemplateInitializerDeprecated(
			final ObjectProvider<List<RestTemplateCustomizer>> restTemplateCustomizers) {
		return () -> restTemplateCustomizers.ifAvailable(customizers -> {
			for (RestTemplate restTemplate : LoadBalancerAutoConfiguration.this.restTemplates) {
				for (RestTemplateCustomizer customizer : customizers) {
					customizer.customize(restTemplate);
				}
			}
		});
	}

	/**
	 * 注入请求工厂，用于创建LoadBalancerRequest进行http请求
	 */
	@Bean
	@ConditionalOnMissingBean
	public LoadBalancerRequestFactory loadBalancerRequestFactory(LoadBalancerClient loadBalancerClient) {
		return new LoadBalancerRequestFactory(loadBalancerClient, this.transformers);
	}

	@Configuration(proxyBeanMethods = false)
	//以下任何一个条件满足将生效：
	//1、没有类org.springframework.retry.support.RetryTemplate
	//2、spring.cloud.loadbalancer.retry.enabled=false
	//</p>
	// 也就是不存在重试机制的情况下生效！！！！！！！！
	@Conditional(RetryMissingOrDisabledCondition.class)
	static class LoadBalancerInterceptorConfig {

		/**
		 * 负载均请求衡拦截器
		 */
		@Bean
		public LoadBalancerInterceptor loadBalancerInterceptor(LoadBalancerClient loadBalancerClient,
		                                                       LoadBalancerRequestFactory requestFactory) {
			return new LoadBalancerInterceptor(loadBalancerClient, requestFactory);
		}

		/**
		 * RestTemplateCustomizer，用于给RestTemplate设置拦截器，LoadBalancerInterceptor
		 */
		@Bean
		@ConditionalOnMissingBean
		public RestTemplateCustomizer restTemplateCustomizer(final LoadBalancerInterceptor loadBalancerInterceptor) {
			return restTemplate -> {
				//给RestTemplate设置拦截器
				List<ClientHttpRequestInterceptor> list = new ArrayList<>(restTemplate.getInterceptors());
				list.add(loadBalancerInterceptor);
				restTemplate.setInterceptors(list);
			};
		}

	}

	private static class RetryMissingOrDisabledCondition extends AnyNestedCondition {

		RetryMissingOrDisabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnMissingClass("org.springframework.retry.support.RetryTemplate")
		static class RetryTemplateMissing {

		}

		@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.enabled", havingValue = "false")
		static class RetryDisabled {

		}

	}

	/**
	 * Auto configuration for retry mechanism.
	 * 注入重试相关bean
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RetryTemplate.class)
	public static class RetryAutoConfiguration {

		/**
		 * 注入重试工厂
		 */
		@Bean
		@ConditionalOnMissingBean
		public LoadBalancedRetryFactory loadBalancedRetryFactory() {
			return new LoadBalancedRetryFactory() {
			};
		}

	}

	/**
	 * Auto configuration for retry intercepting mechanism.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RetryTemplate.class)
	@ConditionalOnBean(ReactiveLoadBalancer.Factory.class)
	@ConditionalOnProperty(value = "spring.cloud.loadbalancer.retry.enabled", matchIfMissing = true)
	//一堆的判断条件
	public static class RetryInterceptorAutoConfiguration {

		/**
		 * RetryLoadBalancerInterceptor
		 */
		@Bean
		@ConditionalOnMissingBean
		public RetryLoadBalancerInterceptor loadBalancerInterceptor(LoadBalancerClient loadBalancerClient,
		                                                            LoadBalancerProperties properties,
		                                                            LoadBalancerRequestFactory requestFactory,
		                                                            LoadBalancedRetryFactory loadBalancedRetryFactory,
		                                                            ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerFactory) {
			return new RetryLoadBalancerInterceptor(loadBalancerClient, properties, requestFactory,
					loadBalancedRetryFactory, loadBalancerFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		public RestTemplateCustomizer restTemplateCustomizer(final RetryLoadBalancerInterceptor loadBalancerInterceptor) {
			return restTemplate -> {
				//给restTemplate设置拦截器
				List<ClientHttpRequestInterceptor> list = new ArrayList<>(restTemplate.getInterceptors());
				list.add(loadBalancerInterceptor);
				restTemplate.setInterceptors(list);
			};
		}

	}

}
