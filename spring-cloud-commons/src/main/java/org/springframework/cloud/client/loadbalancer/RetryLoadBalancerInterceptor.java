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

import java.io.IOException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * @author Ryan Baxter
 * @author Will Tran
 * @author Gang Li
 * @author Olga Maciaszek-Sharma
 */
public class RetryLoadBalancerInterceptor implements ClientHttpRequestInterceptor {

	private static final Log LOG = LogFactory.getLog(RetryLoadBalancerInterceptor.class);

	private final LoadBalancerClient loadBalancer;

	private final LoadBalancerRetryProperties lbProperties;

	private final LoadBalancerRequestFactory requestFactory;

	private final LoadBalancedRetryFactory lbRetryFactory;

	public RetryLoadBalancerInterceptor(LoadBalancerClient loadBalancer,
	                                    LoadBalancerRetryProperties lbProperties,
	                                    LoadBalancerRequestFactory requestFactory,
	                                    LoadBalancedRetryFactory lbRetryFactory) {
		this.loadBalancer = loadBalancer;
		this.lbProperties = lbProperties;
		this.requestFactory = requestFactory;
		this.lbRetryFactory = lbRetryFactory;

	}

	@Override
	public ClientHttpResponse intercept(final HttpRequest request,
	                                    final byte[] body,
	                                    final ClientHttpRequestExecution execution) throws IOException {
		//获取服务名
		final URI originalUri = request.getURI();
		final String serviceName = originalUri.getHost();
		Assert.state(serviceName != null,
				"Request URI does not contain a valid hostname: " + originalUri);

		//创建重试策略
		final LoadBalancedRetryPolicy retryPolicy = lbRetryFactory.createRetryPolicy(serviceName, loadBalancer);
		//创建重试RetryTemplate
		RetryTemplate template = createRetryTemplate(serviceName, request, retryPolicy);
		return template.execute(context -> {
			//从重试上下文中获取服务实例
			ServiceInstance serviceInstance = null;
			if (context instanceof LoadBalancedRetryContext) {
				LoadBalancedRetryContext lbContext = (LoadBalancedRetryContext) context;
				serviceInstance = lbContext.getServiceInstance();
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Retrieved service instance from LoadBalancedRetryContext: %s", serviceInstance));
				}
			}
			//如果重试上下文中没有服务实例
			if (serviceInstance == null) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(
							"Service instance retrieved from LoadBalancedRetryContext: was null. "
									+ "Reattempting service instance selection");
				}
				//重新选择一个服务实例
				serviceInstance = loadBalancer.choose(serviceName);
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Selected service instance: %s",
							serviceInstance));
				}
			}
			//执行请求，获取相应结果
			ClientHttpResponse response = loadBalancer.execute(serviceName, serviceInstance,
					requestFactory.createRequest(request, body, execution));
			int statusCode = response.getRawStatusCode();
			//如果要根据状态码进行重试，此状态码满足重试要求
			if (retryPolicy != null && retryPolicy.retryableStatusCode(statusCode)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug(String.format("Retrying on status code: %d", statusCode));
				}
				byte[] bodyCopy = StreamUtils.copyToByteArray(response.getBody());
				response.close();

				//直接抛出一个异常
				throw new ClientHttpResponseStatusCodeException(serviceName, response, bodyCopy);
			}
			return response;
		}, new LoadBalancedRecoveryCallback<ClientHttpResponse, ClientHttpResponse>() {
			// This is a special case, where both parameters to
			// LoadBalancedRecoveryCallback are
			// the same. In most cases they would be different.
			@Override
			protected ClientHttpResponse createResponse(ClientHttpResponse response, URI uri) {
				return response;
			}
		});
	}

	private RetryTemplate createRetryTemplate(String serviceName,
	                                          HttpRequest request,
	                                          LoadBalancedRetryPolicy retryPolicy) {
		RetryTemplate template = new RetryTemplate();
		//创建回退策略
		BackOffPolicy backOffPolicy = lbRetryFactory.createBackOffPolicy(serviceName);
		//设置回退策略
		template.setBackOffPolicy(backOffPolicy == null ? new NoBackOffPolicy() : backOffPolicy);
		//重试完时抛出最后一个异常
		template.setThrowLastExceptionOnExhausted(true);
		//创建RetryListener
		RetryListener[] retryListeners = lbRetryFactory.createRetryListeners(serviceName);
		//设置RetryListener
		if (retryListeners != null && retryListeners.length != 0) {
			template.setListeners(retryListeners);
		}
		//设置重试策略
		template.setRetryPolicy(!lbProperties.isEnabled() || retryPolicy == null
				? new NeverRetryPolicy()
				: new InterceptorRetryPolicy(request, retryPolicy, loadBalancer, serviceName));

		return template;
	}

}
