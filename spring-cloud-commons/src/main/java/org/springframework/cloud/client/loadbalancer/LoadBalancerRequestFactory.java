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

import java.util.List;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Creates {@link LoadBalancerRequest}s for {@link LoadBalancerInterceptor} and
 * {@link RetryLoadBalancerInterceptor}. Applies {@link LoadBalancerRequestTransformer}s
 * to the intercepted {@link HttpRequest}.
 *
 * @author William Tran
 */

/**
 * 负载均衡请求工厂，用于创建LoadBalancerRequest
 * 会根据LoadBalancerRequestTransformer的实现类，进行请求的转换
 */
public class LoadBalancerRequestFactory {

	/**
	 * 负载均衡客户端
	 */
	private LoadBalancerClient loadBalancer;

	/**
	 * 负载均衡请求转换器
	 */
	private List<LoadBalancerRequestTransformer> transformers;

	public LoadBalancerRequestFactory(LoadBalancerClient loadBalancer,
	                                  List<LoadBalancerRequestTransformer> transformers) {
		this.loadBalancer = loadBalancer;
		this.transformers = transformers;
	}

	public LoadBalancerRequestFactory(LoadBalancerClient loadBalancer) {
		this.loadBalancer = loadBalancer;
	}

	/**
	 * 创建LoadBalancerRequest
	 *
	 * @param request   http请求
	 * @param body      请求体
	 * @param execution 客户端http请求执行
	 * @return LoadBalancerRequest
	 */
	public LoadBalancerRequest<ClientHttpResponse> createRequest(final HttpRequest request,
	                                                             final byte[] body,
	                                                             final ClientHttpRequestExecution execution) {
		return instance -> {
			//创建ServiceRequestWrapper，包装HttpRequest，ServiceInstance，LoadBalancerClient
			HttpRequest serviceRequest = new ServiceRequestWrapper(request, instance, this.loadBalancer);
			if (this.transformers != null) {
				for (LoadBalancerRequestTransformer transformer : this.transformers) {
					//进行请求转换
					serviceRequest = transformer.transformRequest(serviceRequest, instance);
				}
			}
			//执行获得ClientHttpResponse
			return execution.execute(serviceRequest, body);
		};
	}

}
