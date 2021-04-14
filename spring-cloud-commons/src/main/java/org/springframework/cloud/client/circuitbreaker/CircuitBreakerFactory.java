/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.client.circuitbreaker;

/**
 * Creates circuit breakers based on the underlying implementation.
 * <CONF> 断路器的配置信息类型
 * <CONFB> 断路器配置信息创建器ConfigBuilder的实现类
 * todo 需要用户实现
 *
 * @author Ryan Baxter
 */
public abstract class CircuitBreakerFactory<CONF, CONFB extends ConfigBuilder<CONF>>
		extends AbstractCircuitBreakerFactory<CONF, CONFB> {

	/**
	 * 根据断路器id创建一个断路器
	 */
	public abstract CircuitBreaker create(String id);

}
