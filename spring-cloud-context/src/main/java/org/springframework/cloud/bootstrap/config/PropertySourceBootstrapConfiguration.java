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

package org.springframework.cloud.bootstrap.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.logging.LoggingRebinder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * 自定义加载配置，通过实现PropertySourceLocator
 */

/**
 * @author Dave Syer
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropertySourceBootstrapProperties.class)
public class PropertySourceBootstrapConfiguration
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	/**
	 * Bootstrap property source name.
	 * 启动属性的名称，bootstrapProperties
	 */
	public static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = BootstrapApplicationListener.BOOTSTRAP_PROPERTY_SOURCE_NAME
			+ "Properties";

	private static Log logger = LogFactory.getLog(PropertySourceBootstrapConfiguration.class);

	private int order = Ordered.HIGHEST_PRECEDENCE + 10;

	/**
	 * 属性加载器，用户自己实现
	 */
	@Autowired(required = false)
	private List<PropertySourceLocator> propertySourceLocators = new ArrayList<>();

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setPropertySourceLocators(Collection<PropertySourceLocator> propertySourceLocators) {
		this.propertySourceLocators = new ArrayList<>(propertySourceLocators);
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		List<PropertySource<?>> composite = new ArrayList<>();
		//排序
		AnnotationAwareOrderComparator.sort(this.propertySourceLocators);
		boolean empty = true;
		//当前环境
		ConfigurableEnvironment environment = applicationContext.getEnvironment();
		//遍历propertySourceLocators
		for (PropertySourceLocator locator : this.propertySourceLocators) {
			//用户自己实现的属性加载器
			Collection<PropertySource<?>> source = locator.locateCollection(environment);
			if (source == null || source.size() == 0) {
				continue;
			}
			List<PropertySource<?>> sourceList = new ArrayList<>();
			for (PropertySource<?> p : source) {
				//是EnumerablePropertySource
				if (p instanceof EnumerablePropertySource) {
					//使用BootstrapPropertySource进行代理，名称为bootstrapProperties-p.getName()
					EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) p;
					sourceList.add(new BootstrapPropertySource<>(enumerable));
				} else {
					//使用SimpleBootstrapPropertySource代理，名称为bootstrapProperties-p.getName()
					sourceList.add(new SimpleBootstrapPropertySource(p));
				}
			}
			logger.info("Located property source: " + sourceList);
			composite.addAll(sourceList);
			empty = false;
		}
		//存在自定义属性
		if (!empty) {
			//获取当前所有的属性集合
			MutablePropertySources propertySources = environment.getPropertySources();
			//获取日志配置
			String logConfig = environment.resolvePlaceholders("${logging.config:}");
			//获取log信息
			LogFile logFile = LogFile.get(environment);
			for (PropertySource<?> p : environment.getPropertySources()) {
				//移除bootstrapProperties开头的属性集合
				if (p.getName().startsWith(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
					propertySources.remove(p.getName());
				}
			}
			//插入自定义属性
			insertPropertySources(propertySources, composite);
			//重新初始化日志系统
			reinitializeLoggingSystem(environment, logConfig, logFile);
			//设置日志级别
			setLogLevels(applicationContext, environment);
			//处理profile
			handleIncludedProfiles(environment);
		}
	}

	private void reinitializeLoggingSystem(ConfigurableEnvironment environment,
										   String oldLogConfig,
										   LogFile oldLogFile) {
		Map<String, Object> props = Binder.get(environment).bind("logging", Bindable.mapOf(String.class, Object.class))
				.orElseGet(Collections::emptyMap);
		if (!props.isEmpty()) {
			String logConfig = environment.resolvePlaceholders("${logging.config:}");
			LogFile logFile = LogFile.get(environment);
			LoggingSystem system = LoggingSystem.get(LoggingSystem.class.getClassLoader());
			try {
				// Three step initialization that accounts for the clean up of the logging
				// context before initialization. Spring Boot doesn't initialize a logging
				// system that hasn't had this sequence applied (since 1.4.1).
				system.cleanUp();
				system.beforeInitialize();
				system.initialize(new LoggingInitializationContext(environment), logConfig, logFile);
			} catch (Exception ex) {
				PropertySourceBootstrapConfiguration.logger.warn("Error opening logging config file " + logConfig, ex);
			}
		}
	}

	private void setLogLevels(ConfigurableApplicationContext applicationContext, ConfigurableEnvironment environment) {
		LoggingRebinder rebinder = new LoggingRebinder();
		rebinder.setEnvironment(environment);
		// We can't fire the event in the ApplicationContext here (too early), but we can
		// create our own listener and poke it (it doesn't need the key changes)
		//发布环境改变事件
		//日志改变
		rebinder.onApplicationEvent(new EnvironmentChangeEvent(applicationContext, Collections.emptySet()));
	}

	/**
	 * @param propertySources 当前属性集合
	 * @param composite       自定义属性集合
	 */
	private void insertPropertySources(MutablePropertySources propertySources, List<PropertySource<?>> composite) {
		MutablePropertySources incoming = new MutablePropertySources();
		List<PropertySource<?>> reversedComposite = new ArrayList<>(composite);
		// Reverse the list so that when we call addFirst below we are maintaining the
		// same order of PropertySources
		// Wherever we call addLast we can use the order in the List since the first item
		// will end up before the rest
		Collections.reverse(reversedComposite);
		for (PropertySource<?> p : reversedComposite) {
			incoming.addFirst(p);
		}
		//incoming和reversedComposite是逆序的
		PropertySourceBootstrapProperties remoteProperties = new PropertySourceBootstrapProperties();
		//environment()方法进行了反序操作，使得构造的环境是正序的
		Binder.get(environment(incoming)).bind("spring.cloud.config", Bindable.ofInstance(remoteProperties));

		//不允许覆盖
		//或者
		//允许覆盖系统属性且不是最低优先级
		//isAllowOverride默认是true
		//isOverrideNone默认是false
		//isOverrideSystemProperties默认是true
		//默认会进入这个分支
		if (!remoteProperties.isAllowOverride()
				|| (!remoteProperties.isOverrideNone() && remoteProperties.isOverrideSystemProperties())) {
			for (PropertySource<?> p : reversedComposite) {
				propertySources.addFirst(p);
			}
			return;
		}
		//优先级最低，不能覆盖其他属性
		if (remoteProperties.isOverrideNone()) {
			for (PropertySource<?> p : composite) {
				//添加到末尾
				propertySources.addLast(p);
			}
			return;
		}
		//如果原来属性集合包括系统属性
		if (propertySources.contains(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
			//不能覆盖系统属性
			if (!remoteProperties.isOverrideSystemProperties()) {
				for (PropertySource<?> p : reversedComposite) {
					//添加到系统属性之后
					propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, p);
				}
			} else {
				//能覆盖系统属性
				for (PropertySource<?> p : composite) {
					//添加到系统属性之前
					propertySources.addBefore(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, p);
				}
			}
		} else {
			//不包含，添加到最后
			for (PropertySource<?> p : composite) {
				propertySources.addLast(p);
			}
		}
	}

	private Environment environment(MutablePropertySources incoming) {
		StandardEnvironment environment = new StandardEnvironment();
		for (PropertySource<?> source : environment.getPropertySources()) {
			//environment清空
			environment.getPropertySources().remove(source.getName());
		}
		for (PropertySource<?> source : incoming) {
			//反序添加，和原来一样
			environment.getPropertySources().addLast(source);
		}
		return environment;
	}

	private void handleIncludedProfiles(ConfigurableEnvironment environment) {
		//includeProfiles
		Set<String> includeProfiles = new TreeSet<>();
		for (PropertySource<?> propertySource : environment.getPropertySources()) {
			//获取环境中的profile
			addIncludedProfilesTo(includeProfiles, propertySource);
		}

		//activeProfiles
		List<String> activeProfiles = new ArrayList<>();
		Collections.addAll(activeProfiles, environment.getActiveProfiles());

		// If it's already accepted we assume the order was set intentionally
		//includeProfiles移除活动的profile
		includeProfiles.removeAll(activeProfiles);
		if (includeProfiles.isEmpty()) {
			return;
		}
		// Prepend each added profile (last wins in a property key clash)
		for (String profile : includeProfiles) {
			activeProfiles.add(0, profile);
		}
		environment.setActiveProfiles(activeProfiles.toArray(new String[activeProfiles.size()]));
	}

	/**
	 * @param profiles       profiles
	 * @param propertySource 原始属性
	 * @return profiles
	 */
	private Set<String> addIncludedProfilesTo(Set<String> profiles, PropertySource<?> propertySource) {
		//组合属性
		if (propertySource instanceof CompositePropertySource) {
			for (PropertySource<?> nestedPropertySource : ((CompositePropertySource) propertySource).getPropertySources()) {
				addIncludedProfilesTo(profiles, nestedPropertySource);
			}
		} else {
			Collections.addAll(profiles, getProfilesForValue(propertySource.getProperty(ConfigFileApplicationListener.INCLUDE_PROFILES_PROPERTY)));
		}
		return profiles;
	}

	private String[] getProfilesForValue(Object property) {
		final String value = (property == null ? null : property.toString());
		return property == null ? new String[0] : StringUtils.tokenizeToStringArray(value, ",");
	}

}
