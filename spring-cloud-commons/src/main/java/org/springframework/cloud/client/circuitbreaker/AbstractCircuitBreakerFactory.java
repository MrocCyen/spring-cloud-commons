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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for factories which produce circuit breakers.
 *
 * @author Ryan Baxter
 */
public abstract class AbstractCircuitBreakerFactory<CONF, CONFB extends ConfigBuilder<CONF>> {

	/**
	 * 保存断路器配置信息，key是断路器id
	 */
	private final ConcurrentHashMap<String, CONF> configurations = new ConcurrentHashMap<>();

	/**
	 * Adds configurations for circuit breakers.
	 *
	 * @param ids      The id of the circuit breaker，断路器的id列表
	 * @param consumer A configuration builder consumer, allows consumers to customize the
	 *                 builder before the configuration is built
	 */
	public void configure(Consumer<CONFB> consumer, String... ids) {
		for (String id : ids) {
			//获取配置信息构建器
			CONFB builder = configBuilder(id);
			//自定义处理配置信息构建器
			consumer.accept(builder);
			//创建配置信息
			CONF conf = builder.build();
			//保存每个断路器的id和各自的配置信息
			getConfigurations().put(id, conf);
		}
	}

	/**
	 * Gets the configurations for the circuit breakers.
	 *
	 * @return The configurations
	 */
	protected ConcurrentHashMap<String, CONF> getConfigurations() {
		return configurations;
	}

	/**
	 * Creates a configuration builder for the given id.
	 * 根据id获取这个id对应的断路器配置信息构建器
	 *
	 * @param id The id of the circuit breaker
	 * @return The configuration builder
	 */
	protected abstract CONFB configBuilder(String id);

	/**
	 * Sets the default configuration for circuit breakers.
	 *
	 * @param defaultConfiguration A function that returns the default configuration
	 */
	public abstract void configureDefault(Function<String, CONF> defaultConfiguration);

}
