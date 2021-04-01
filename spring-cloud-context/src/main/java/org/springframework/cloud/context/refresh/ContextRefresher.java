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

package org.springframework.cloud.context.refresh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * @author Dave Syer
 * @author Venil Noronha
 */

/**
 * 用于刷新上下文context
 */
public abstract class ContextRefresher {

	protected final Log logger = LogFactory.getLog(getClass());

	//刷新属性名
	protected static final String REFRESH_ARGS_PROPERTY_SOURCE = "refreshArgs";

	//默认属性名
	protected static final String[] DEFAULT_PROPERTY_SOURCES = new String[]{
			// order matters, if cli args aren't first, things get messy
			CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME, "defaultProperties"};

	//环境中本来标准的属性名
	protected Set<String> standardSources = new HashSet<>(
			Arrays.asList(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME, "configurationProperties"));

	protected final List<String> additionalPropertySourcesToRetain;

	private ConfigurableApplicationContext context;

	private RefreshScope scope;

	@Deprecated
	protected ContextRefresher(ConfigurableApplicationContext context, RefreshScope scope) {
		this(context, scope, new RefreshAutoConfiguration.RefreshProperties());
	}

	@SuppressWarnings("unchecked")
	protected ContextRefresher(ConfigurableApplicationContext context, RefreshScope scope,
							   RefreshAutoConfiguration.RefreshProperties properties) {
		this.context = context;
		this.scope = scope;
		additionalPropertySourcesToRetain = properties.getAdditionalPropertySourcesToRetain();
	}

	protected ConfigurableApplicationContext getContext() {
		return this.context;
	}

	protected RefreshScope getScope() {
		return this.scope;
	}

	public synchronized Set<String> refresh() {
		//更新环境
		Set<String> keys = refreshEnvironment();
		//todo 刷新所有的scope
		this.scope.refreshAll();

		return keys;
	}

	/**
	 * 刷新环境
	 *
	 * @return
	 */
	public synchronized Set<String> refreshEnvironment() {
		//提取出不是环境系统标准的属性值，before
		Map<String, Object> before = extract(this.context.getEnvironment().getPropertySources());
		//模板方法，更新环境，子类实现
		updateEnvironment();
		//1、先提取出变化后的环境的值，extract(this.context.getEnvironment().getPropertySources())
		//2、进行改变环境
		Set<String> keys = changes(before, extract(this.context.getEnvironment().getPropertySources())).keySet();
		//发布环境变更事件
		this.context.publishEvent(new EnvironmentChangeEvent(this.context, keys));

		return keys;
	}

	protected abstract void updateEnvironment();

	// Don't use ConfigurableEnvironment.merge() in case there are clashes with property
	// source names
	protected StandardEnvironment copyEnvironment(ConfigurableEnvironment input) {
		StandardEnvironment environment = new StandardEnvironment();
		MutablePropertySources capturedPropertySources = environment.getPropertySources();
		// Only copy the default property source(s) and the profiles over from the main
		// environment (everything else should be pristine, just like it was on startup).
		List<String> propertySourcesToRetain = new ArrayList<>(Arrays.asList(DEFAULT_PROPERTY_SOURCES));
		if (!CollectionUtils.isEmpty(additionalPropertySourcesToRetain)) {
			propertySourcesToRetain.addAll(additionalPropertySourcesToRetain);
		}

		for (String name : propertySourcesToRetain) {
			if (input.getPropertySources().contains(name)) {
				if (capturedPropertySources.contains(name)) {
					capturedPropertySources.replace(name, input.getPropertySources().get(name));
				} else {
					capturedPropertySources.addLast(input.getPropertySources().get(name));
				}
			}
		}
		environment.setActiveProfiles(input.getActiveProfiles());
		environment.setDefaultProfiles(input.getDefaultProfiles());
		return environment;
	}

	/**
	 * 前后环境变化的处理
	 *
	 * @param before
	 * @param after
	 * @return
	 */
	private Map<String, Object> changes(Map<String, Object> before, Map<String, Object> after) {
		Map<String, Object> result = new HashMap<>();
		//遍历之前的环境
		for (String key : before.keySet()) {
			//如果之后的环境没有当前key对应的值
			if (!after.containsKey(key)) {
				//保存null到result
				result.put(key, null);
			} else if (!equal(before.get(key), after.get(key))) {
				//如果之前和之后都存在值
				//这两个值不相等，则保存之后的值到result
				result.put(key, after.get(key));
			}
		}
		//遍历之后的环境
		for (String key : after.keySet()) {
			//如果不在之前的环境中，则直接保存到result
			if (!before.containsKey(key)) {
				result.put(key, after.get(key));
			}
		}
		return result;
	}

	private boolean equal(Object one, Object two) {
		if (one == null && two == null) {
			return true;
		}
		if (one == null || two == null) {
			return false;
		}
		return one.equals(two);
	}

	//不是系统标准的属性提取出来
	private Map<String, Object> extract(MutablePropertySources propertySources) {
		Map<String, Object> result = new HashMap<>();
		List<PropertySource<?>> sources = new ArrayList<>();

		for (PropertySource<?> source : propertySources) {
			//添加到first
			sources.add(0, source);
		}

		for (PropertySource<?> source : sources) {
			//不是系统标准属性，单独处理一下
			if (!this.standardSources.contains(source.getName())) {
				extract(source, result);
			}
		}

		return result;
	}

	private void extract(PropertySource<?> parent, Map<String, Object> result) {
		if (parent instanceof CompositePropertySource) {
			try {
				List<PropertySource<?>> sources = new ArrayList<>();
				for (PropertySource<?> source : ((CompositePropertySource) parent).getPropertySources()) {
					//添加到first
					sources.add(0, source);
				}
				for (PropertySource<?> source : sources) {
					//递归添加CompositePropertySource内的PropertySource
					extract(source, result);
				}
			} catch (Exception e) {
				return;
			}
		} else if (parent instanceof EnumerablePropertySource) {
			//将属性值添加进result中
			for (String key : ((EnumerablePropertySource<?>) parent).getPropertyNames()) {
				result.put(key, parent.getProperty(key));
			}
		}
	}

	@Configuration(proxyBeanMethods = false)
	protected static class Empty {

	}

}
