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

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;

/**
 * 拦截客户端请求，可以进行扩展操作
 * 默认是关闭的
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Ryan Baxter
 * @author William Tran
 */
public class LoadBalancerInterceptor implements ClientHttpRequestInterceptor {

	private LoadBalancerClient loadBalancer;

	private LoadBalancerRequestFactory requestFactory;

	public LoadBalancerInterceptor(LoadBalancerClient loadBalancer, LoadBalancerRequestFactory requestFactory) {
		this.loadBalancer = loadBalancer;
		this.requestFactory = requestFactory;
	}

	public LoadBalancerInterceptor(LoadBalancerClient loadBalancer) {
		// for backwards compatibility
		this(loadBalancer, new LoadBalancerRequestFactory(loadBalancer));
	}

	/**
	 * 直接拦截请求
	 *
	 * @param request
	 * @param body
	 * @param execution
	 * @return
	 * @throws IOException
	 */
	@Override
	public ClientHttpResponse intercept(final HttpRequest request,
	                                    final byte[] body,
	                                    final ClientHttpRequestExecution execution) throws IOException {
		final URI originalUri = request.getURI();
		String serviceName = originalUri.getHost();
		Assert.state(serviceName != null, "Request URI does not contain a valid hostname: " + originalUri);

		//1、先调用LoadBalancerRequestFactory#createRequest获取LoadBalancerRequest，内部进行请求的转换
		//2、在调用LoadBalancerClient#execute执行请求
		LoadBalancerRequest<ClientHttpResponse> factoryRequest = this.requestFactory.createRequest(request, body, execution);
		return this.loadBalancer.execute(serviceName, factoryRequest);
	}

}
