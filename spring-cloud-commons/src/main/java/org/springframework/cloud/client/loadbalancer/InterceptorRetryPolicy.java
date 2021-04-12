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

import org.springframework.http.HttpRequest;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;

/**
 * {@link RetryPolicy} used by the {@link LoadBalancerClient} when retrying failed
 * requests.
 *
 * @author Ryan Baxter
 * @author Olga Maciaszek-Sharma
 */
//http请求的重试策略，这里类名改成RetryInterceptorPolicy更为贴切
//用于RetryLoadBalancerInterceptor，拦截请求，设置restTemplate的重试策略
//内部使用LoadBalancedRetryPolicy进行真正的处理
public class InterceptorRetryPolicy implements RetryPolicy {

	//http请求
	private final HttpRequest request;

	//负载均衡的重试策略
	private final LoadBalancedRetryPolicy policy;

	//服务选择器
	private final ServiceInstanceChooser serviceInstanceChooser;

	//服务名称
	private final String serviceName;

	/**
	 * Creates a new retry policy.
	 *
	 * @param request                The request that will be retried.
	 * @param policy                 The retry policy from the load balancer.
	 * @param serviceInstanceChooser The load balancer client.
	 * @param serviceName            The name of the service.
	 */
	public InterceptorRetryPolicy(HttpRequest request,
	                              LoadBalancedRetryPolicy policy,
	                              ServiceInstanceChooser serviceInstanceChooser,
	                              String serviceName) {
		this.request = request;
		this.policy = policy;
		this.serviceInstanceChooser = serviceInstanceChooser;
		this.serviceName = serviceName;
	}

	//是否可以进行重试
	@Override
	public boolean canRetry(RetryContext context) {
		LoadBalancedRetryContext lbContext = (LoadBalancedRetryContext) context;
		//还没有重试过
		if (lbContext.getRetryCount() == 0 && lbContext.getServiceInstance() == null) {
			// We haven't even tried to make the request yet so return true so we do
			lbContext.setServiceInstance(null);
			return true;
		}
		//使用负载均衡策略进行判断，重试下一台服务器
		return policy.canRetryNextServer(lbContext);
	}

	@Override
	public RetryContext open(RetryContext parent) {
		return new LoadBalancedRetryContext(parent, request);
	}

	@Override
	public void close(RetryContext context) {
		policy.close((LoadBalancedRetryContext) context);
	}

	@Override
	public void registerThrowable(RetryContext context, Throwable throwable) {
		LoadBalancedRetryContext lbContext = (LoadBalancedRetryContext) context;
		// this is important as it registers the last exception in the context and also
		// increases the retry count
		lbContext.registerThrowable(throwable);
		// let the policy know about the exception as well
		policy.registerThrowable(lbContext, throwable);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		InterceptorRetryPolicy that = (InterceptorRetryPolicy) o;

		if (!request.equals(that.request)) {
			return false;
		}
		if (!policy.equals(that.policy)) {
			return false;
		}
		if (!serviceInstanceChooser.equals(that.serviceInstanceChooser)) {
			return false;
		}
		return serviceName.equals(that.serviceName);

	}

	@Override
	public int hashCode() {
		int result = request.hashCode();
		result = 31 * result + policy.hashCode();
		result = 31 * result + serviceInstanceChooser.hashCode();
		result = 31 * result + serviceName.hashCode();
		return result;
	}

}
