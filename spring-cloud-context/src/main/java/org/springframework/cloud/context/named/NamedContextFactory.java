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

package org.springframework.cloud.context.named;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.MapPropertySource;

/**
 * Creates a set of child contexts that allows a set of Specifications to define the beans
 * in each child context.
 * <p>
 * Ported from spring-cloud-netflix FeignClientFactory and SpringClientFactory
 *
 * @param <C> specification
 * @author Spencer Gibb
 * @author Dave Syer
 */
public abstract class NamedContextFactory<C extends NamedContextFactory.Specification>
		implements DisposableBean, ApplicationContextAware {

	private final String propertySourceName;

	private final String propertyName;

	/**
	 * 存储在多个上下文context
	 */
	private Map<String, AnnotationConfigApplicationContext> contexts = new ConcurrentHashMap<>();

	/**
	 * 存储Specification的map
	 */
	private Map<String, C> configurations = new ConcurrentHashMap<>();

	/**
	 * 父级context
	 */
	private ApplicationContext parent;

	/**
	 * 默认的配置类
	 */
	private Class<?> defaultConfigType;

	public NamedContextFactory(Class<?> defaultConfigType, String propertySourceName, String propertyName) {
		this.defaultConfigType = defaultConfigType;
		this.propertySourceName = propertySourceName;
		this.propertyName = propertyName;
	}

	@Override
	public void setApplicationContext(ApplicationContext parent) throws BeansException {
		//设置父级context
		this.parent = parent;
	}

	public void setConfigurations(List<C> configurations) {
		//设置Specification
		for (C client : configurations) {
			this.configurations.put(client.getName(), client);
		}
	}

	public Set<String> getContextNames() {
		//获取存储context的map的key的集合
		return new HashSet<>(this.contexts.keySet());
	}

	@Override
	public void destroy() {
		Collection<AnnotationConfigApplicationContext> values = this.contexts.values();
		for (AnnotationConfigApplicationContext context : values) {
			// This can fail, but it never throws an exception (you see stack traces
			// logged as WARN).
			//关闭每个context
			context.close();
		}
		this.contexts.clear();
	}

	protected AnnotationConfigApplicationContext getContext(String name) {
		if (!this.contexts.containsKey(name)) {
			synchronized (this.contexts) {
				if (!this.contexts.containsKey(name)) {
					this.contexts.put(name, createContext(name));
				}
			}
		}
		return this.contexts.get(name);
	}

	/**
	 * 创建context
	 *
	 * @param name context对应map中的名称
	 * @return
	 */
	protected AnnotationConfigApplicationContext createContext(String name) {
		//新建一个AnnotationConfigApplicationContext
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		//当前context存在需要注册的bean
		if (this.configurations.containsKey(name)) {
			for (Class<?> configuration : this.configurations.get(name).getConfiguration()) {
				//注册bean
				context.register(configuration);
			}
		}
		for (Map.Entry<String, C> entry : this.configurations.entrySet()) {
			//如果存在key为default.开头，则会直接注册进context
			if (entry.getKey().startsWith("default.")) {
				for (Class<?> configuration : entry.getValue().getConfiguration()) {
					context.register(configuration);
				}
			}
		}
		//注册PropertyPlaceholderAutoConfiguration，处理占位符
		//注册defaultConfigType
		context.register(PropertyPlaceholderAutoConfiguration.class, this.defaultConfigType);
		//为context注册一个property，值为name
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(this.propertySourceName,
				Collections.singletonMap(this.propertyName, name)));
		if (this.parent != null) {
			// Uses Environment from parent as well as beans
			context.setParent(this.parent);
			// jdk11 issue
			// https://github.com/spring-cloud/spring-cloud-netflix/issues/3101
			context.setClassLoader(this.parent.getClassLoader());
		}
		context.setDisplayName(generateDisplayName(name));
		//刷新context
		context.refresh();
		return context;
	}

	protected String generateDisplayName(String name) {
		return this.getClass().getSimpleName() + "-" + name;
	}

	//从名称为name的context中获取类型是type的bean实例
	public <T> T getInstance(String name, Class<T> type) {
		AnnotationConfigApplicationContext context = getContext(name);
		try {
			return context.getBean(type);
		} catch (NoSuchBeanDefinitionException e) {
			// ignore
		}
		return null;
	}

	public <T> ObjectProvider<T> getLazyProvider(String name, Class<T> type) {
		return new ClientFactoryObjectProvider<>(this, name, type);
	}

	//获取ObjectProvider
	//name为context的名称
	public <T> ObjectProvider<T> getProvider(String name, Class<T> type) {
		AnnotationConfigApplicationContext context = getContext(name);
		return context.getBeanProvider(type);
	}

	public <T> T getInstance(String name, Class<?> clazz, Class<?>... generics) {
		ResolvableType type = ResolvableType.forClassWithGenerics(clazz, generics);
		return getInstance(name, type);
	}

	@SuppressWarnings("unchecked")
	public <T> T getInstance(String name, ResolvableType type) {
		AnnotationConfigApplicationContext context = getContext(name);
		String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context, type);
		if (beanNames.length > 0) {
			for (String beanName : beanNames) {
				if (context.isTypeMatch(beanName, type)) {
					return (T) context.getBean(beanName);
				}
			}
		}
		return null;
	}

	public <T> Map<String, T> getInstances(String name, Class<T> type) {
		AnnotationConfigApplicationContext context = getContext(name);

		return BeanFactoryUtils.beansOfTypeIncludingAncestors(context, type);
	}

	/**
	 * Specification with name and configuration.
	 */
	/**
	 * 封装每个context中的bean
	 */
	public interface Specification {

		//context的名字
		String getName();

		//bean的类型
		Class<?>[] getConfiguration();

	}

}
