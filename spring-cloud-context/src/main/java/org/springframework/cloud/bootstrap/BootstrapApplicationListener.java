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

package org.springframework.cloud.bootstrap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.cloud.bootstrap.encrypt.EnvironmentDecryptApplicationInitializer;
import org.springframework.cloud.bootstrap.support.OriginTrackedCompositePropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.util.PropertyUtils.bootstrapEnabled;
import static org.springframework.cloud.util.PropertyUtils.useLegacyProcessing;

/**
 * A listener that prepares a SpringApplication (e.g. populating its Environment) by
 * delegating to {@link ApplicationContextInitializer} beans in a separate bootstrap
 * context. The bootstrap context is a SpringApplication created from sources defined in
 * spring.factories as {@link BootstrapConfiguration}, and initialized with external
 * config taken from "bootstrap.properties" (or yml), instead of the normal
 * "application.properties".
 *
 * @author Dave Syer
 */
public class BootstrapApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	/**
	 * Property source name for bootstrap.
	 */
	public static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = "bootstrap";

	/**
	 * The default order for this listener.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

	/**
	 * The name of the default properties.
	 */
	public static final String DEFAULT_PROPERTIES = "springCloudDefaultProperties";

	private int order = DEFAULT_ORDER;

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		//获取当前环境
		ConfigurableEnvironment environment = event.getEnvironment();
		//条件判断
		if (!bootstrapEnabled(environment) && !useLegacyProcessing(environment)) {
			return;
		}
		// don't listen to events in a bootstrap context
		//如果当前环境中存在名为bootstrap的PropertySource
		if (environment.getPropertySources().contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
			return;
		}
		ConfigurableApplicationContext context = null;
		//启动上下文名称，这里解决占位符的问题
		//没有配置的话默认是bootstrap
		String configName = environment.resolvePlaceholders("${spring.cloud.bootstrap.name:bootstrap}");
		//获取当前springapplication中的ApplicationContextInitializer
		for (ApplicationContextInitializer<?> initializer : event.getSpringApplication().getInitializers()) {
			//如果存在ParentContextApplicationContextInitializer，则直接赋值给context
			if (initializer instanceof ParentContextApplicationContextInitializer) {
				//寻找父级启动上下文
				context = findBootstrapContext((ParentContextApplicationContextInitializer) initializer, configName);
			}
		}
		if (context == null) {
			//创建父级启动上下文
			context = bootstrapServiceContext(environment, event.getSpringApplication(), configName);
			//添加当前springapplication的close监听器
			event.getSpringApplication().addListeners(new CloseContextOnFailureApplicationListener(context));
		}

		//处理父级上下文
		apply(context, event.getSpringApplication(), environment);
	}

	/**
	 * 寻找父级启动上下文
	 *
	 * @param initializer
	 * @param configName
	 * @return
	 */
	private ConfigurableApplicationContext findBootstrapContext(ParentContextApplicationContextInitializer initializer,
																String configName) {
		Field field = ReflectionUtils.findField(ParentContextApplicationContextInitializer.class, "parent");
		ReflectionUtils.makeAccessible(field);
		//找到父级上下文
		ConfigurableApplicationContext parent = safeCast(ConfigurableApplicationContext.class,
				ReflectionUtils.getField(field, initializer));
		if (parent != null && !configName.equals(parent.getId())) {
			parent = safeCast(ConfigurableApplicationContext.class, parent.getParent());
		}
		return parent;
	}

	private <T> T safeCast(Class<T> type, Object object) {
		try {
			return type.cast(object);
		} catch (ClassCastException e) {
			return null;
		}
	}

	private ConfigurableApplicationContext bootstrapServiceContext(ConfigurableEnvironment environment,
																   final SpringApplication application,
																   String configName) {
		//创建一个标准的环境
		StandardEnvironment bootstrapEnvironment = new StandardEnvironment();
		MutablePropertySources bootstrapProperties = bootstrapEnvironment.getPropertySources();
		//移除所有的PropertySource
		for (PropertySource<?> source : bootstrapProperties) {
			bootstrapProperties.remove(source.getName());
		}
		String configLocation = environment.resolvePlaceholders("${spring.cloud.bootstrap.location:}");
		String configAdditionalLocation = environment.resolvePlaceholders("${spring.cloud.bootstrap.additional-location:}");
		Map<String, Object> bootstrapMap = new HashMap<>();
		bootstrapMap.put("spring.config.name", configName);
		// if an app (or test) uses spring.main.web-application-type=reactive, bootstrap
		// will fail
		// force the environment to use none, because if though it is set below in the
		// builder
		// the environment overrides it
		bootstrapMap.put("spring.main.web-application-type", "none");
		if (StringUtils.hasText(configLocation)) {
			bootstrapMap.put("spring.config.location", configLocation);
		}
		if (StringUtils.hasText(configAdditionalLocation)) {
			bootstrapMap.put("spring.config.additional-location", configAdditionalLocation);
		}
		//添加名为bootstrap的MapPropertySource
		bootstrapProperties.addFirst(new MapPropertySource(BOOTSTRAP_PROPERTY_SOURCE_NAME, bootstrapMap));

		//将原始环境添加到新环境中
		for (PropertySource<?> source : environment.getPropertySources()) {
			if (source instanceof StubPropertySource) {
				continue;
			}
			bootstrapProperties.addLast(source);
		}

		// TODO: is it possible or sensible to share a ResourceLoader?
		SpringApplicationBuilder builder = new SpringApplicationBuilder().profiles(environment.getActiveProfiles())
				.bannerMode(Mode.OFF).environment(bootstrapEnvironment)
				// Don't use the default properties in this builder
				.registerShutdownHook(false).logStartupInfo(false).web(WebApplicationType.NONE);
		final SpringApplication builderApplication = builder.application();
		if (builderApplication.getMainApplicationClass() == null) {
			// gh_425:
			// SpringApplication cannot deduce the MainApplicationClass here
			// if it is booted from SpringBootServletInitializer due to the
			// absense of the "main" method in stackTraces.
			// But luckily this method's second parameter "application" here
			// carries the real MainApplicationClass which has been explicitly
			// set by SpringBootServletInitializer itself already.
			builder.main(application.getMainApplicationClass());
		}
		//过滤掉LoggingApplicationListener和LoggingSystemShutdownListener
		if (environment.getPropertySources().contains("refreshArgs")) {
			// If we are doing a context refresh, really we only want to refresh the
			// Environment, and there are some toxic listeners (like the
			// LoggingApplicationListener) that affect global static state, so we need a
			// way to switch those off.
			builderApplication.setListeners(filterListeners(builderApplication.getListeners()));
		}
		builder.sources(BootstrapImportSelectorConfiguration.class);
		//构建启动上下文
		final ConfigurableApplicationContext context = builder.run();
		// gh-214 using spring.application.name=bootstrap to set the context id via
		// `ContextIdApplicationContextInitializer` prevents apps from getting the actual
		// spring.application.name
		// during the bootstrap phase.
		//设置id
		context.setId("bootstrap");
		// Make the bootstrap context a parent of the app context
		//application：原始application
		//context：新构建的启动上下文
		addAncestorInitializer(application, context);
		// It only has properties in it now that we don't want in the parent so remove
		// it (and it will be added back later)
		//移除名为bootstrap的MapPropertySource
		bootstrapProperties.remove(BOOTSTRAP_PROPERTY_SOURCE_NAME);
		//合并属性
		mergeDefaultProperties(environment.getPropertySources(), bootstrapProperties);

		return context;
	}

	private Collection<? extends ApplicationListener<?>> filterListeners(Set<ApplicationListener<?>> listeners) {
		Set<ApplicationListener<?>> result = new LinkedHashSet<>();
		for (ApplicationListener<?> listener : listeners) {
			if (!(listener instanceof LoggingApplicationListener)
					&& !(listener instanceof LoggingSystemShutdownListener)) {
				result.add(listener);
			}
		}
		return result;
	}

	private void mergeDefaultProperties(MutablePropertySources environment, MutablePropertySources bootstrap) {
		String name = DEFAULT_PROPERTIES;
		//如果包含springCloudDefaultProperties的属性
		if (bootstrap.contains(name)) {
			PropertySource<?> source = bootstrap.get(name);
			if (!environment.contains(name)) {
				environment.addLast(source);
			} else {
				PropertySource<?> target = environment.get(name);
				if (target instanceof MapPropertySource && target != source && source instanceof MapPropertySource) {
					Map<String, Object> targetMap = ((MapPropertySource) target).getSource();
					Map<String, Object> map = ((MapPropertySource) source).getSource();
					for (String key : map.keySet()) {
						if (!target.containsProperty(key)) {
							targetMap.put(key, map.get(key));
						}
					}
				}
			}
		}
		//合并
		mergeAdditionalPropertySources(environment, bootstrap);
	}

	/**
	 * @param environment 原始环境
	 * @param bootstrap   启动环境
	 */
	private void mergeAdditionalPropertySources(MutablePropertySources environment, MutablePropertySources bootstrap) {
		//获取名为springCloudDefaultProperties的PropertySources
		PropertySource<?> defaultProperties = environment.get(DEFAULT_PROPERTIES);
		//获取名为springCloudDefaultProperties的PropertySources
		ExtendedDefaultPropertySource result = defaultProperties instanceof ExtendedDefaultPropertySource
				? (ExtendedDefaultPropertySource) defaultProperties
				//构造名为springCloudDefaultProperties的PropertySources
				: new ExtendedDefaultPropertySource(DEFAULT_PROPERTIES, defaultProperties);
		//遍历启动上下文的属性，取出不在原始环境中的属性值
		for (PropertySource<?> source : bootstrap) {
			//如果属性不在原始的属性中
			if (!environment.contains(source.getName())) {
				//添加至result中，保存在内部的可用于跟踪的组合属性中
				result.add(source);
			}
		}
		//遍历result，在bootstrap中移除这些属性
		for (String name : result.getPropertySourceNames()) {
			bootstrap.remove(name);
		}
		//****************目前的环境是：result的组合属性中存储的是原始环境和启动环境都没有的值**********************
		addOrReplace(environment, result);
		addOrReplace(bootstrap, result);
	}

	/**
	 * 将result设置成名为springCloudDefaultProperties的PropertySources
	 *
	 * @param environment
	 * @param result      是
	 */
	private void addOrReplace(MutablePropertySources environment, PropertySource<?> result) {
		if (environment.contains(result.getName())) {
			environment.replace(result.getName(), result);
		} else {
			environment.addLast(result);
		}
	}

	/**
	 * 给当前应用程序添加祖先初始化器，祖先初始化器持有启动上下文的引用
	 *
	 * @param application 当前应用程序
	 * @param context     启动上下文
	 */
	private void addAncestorInitializer(SpringApplication application, ConfigurableApplicationContext context) {
		boolean installed = false;
		//检查当前上下文程序中是否存在AncestorInitializer，存在则设置新的父级上下文，即启动上下文
		for (ApplicationContextInitializer<?> initializer : application.getInitializers()) {
			if (initializer instanceof AncestorInitializer) {
				installed = true;
				// New parent
				((AncestorInitializer) initializer).setParent(context);
			}
		}
		//没有则添加新的AncestorInitializer
		if (!installed) {
			application.addInitializers(new AncestorInitializer(context));
		}

	}

	/**
	 * @param context     父级上下文
	 * @param application 当前应用程序
	 * @param environment 当前应用程序环境
	 */
	@SuppressWarnings("unchecked")
	private void apply(ConfigurableApplicationContext context, SpringApplication application,
					   ConfigurableEnvironment environment) {
		//包含启动标志配置类，直接返回
		if (application.getAllSources().contains(BootstrapMarkerConfiguration.class)) {
			return;
		}
		//添加source
		application.addPrimarySources(Arrays.asList(BootstrapMarkerConfiguration.class));
		//获取当前应用程序中所有的ApplicationContextInitializer
		@SuppressWarnings("rawtypes")
		Set target = new LinkedHashSet<>(application.getInitializers());
		//添加父级上下文中的ApplicationContextInitializer
		target.addAll(getOrderedBeansOfType(context, ApplicationContextInitializer.class));
		//target中包含父级和当前级的ApplicationContextInitializer，并设置给当前应用程序
		application.setInitializers(target);
		addBootstrapDecryptInitializer(application);
	}

	@SuppressWarnings("unchecked")
	private void addBootstrapDecryptInitializer(SpringApplication application) {
		DelegatingEnvironmentDecryptApplicationInitializer decrypter = null;
		Set<ApplicationContextInitializer<?>> initializers = new LinkedHashSet<>();
		for (ApplicationContextInitializer<?> ini : application.getInitializers()) {
			if (ini instanceof EnvironmentDecryptApplicationInitializer) {
				@SuppressWarnings("rawtypes")
				ApplicationContextInitializer del = ini;
				decrypter = new DelegatingEnvironmentDecryptApplicationInitializer(del);
				initializers.add(ini);
				initializers.add(decrypter);
			} else if (ini instanceof DelegatingEnvironmentDecryptApplicationInitializer) {
				// do nothing
			} else {
				initializers.add(ini);
			}
		}
		ArrayList<ApplicationContextInitializer<?>> target = new ArrayList<ApplicationContextInitializer<?>>(
				initializers);
		application.setInitializers(target);
	}

	private <T> List<T> getOrderedBeansOfType(ListableBeanFactory context, Class<T> type) {
		List<T> result = new ArrayList<T>();
		for (String name : context.getBeanNamesForType(type)) {
			result.add(context.getBean(name, type));
		}
		AnnotationAwareOrderComparator.sort(result);
		return result;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	private static class BootstrapMarkerConfiguration {

	}

	/**
	 * 祖先应用程序初始化器
	 */
	private static class AncestorInitializer
			implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

		/**
		 * 启动上下文
		 */
		private ConfigurableApplicationContext parent;

		AncestorInitializer(ConfigurableApplicationContext parent) {
			this.parent = parent;
		}

		public void setParent(ConfigurableApplicationContext parent) {
			this.parent = parent;
		}

		@Override
		public int getOrder() {
			// Need to run not too late (so not unordered), so that, for instance, the
			// ContextIdApplicationContextInitializer runs later and picks up the merged
			// Environment. Also needs to be quite early so that other initializers can
			// pick up the parent (especially the Environment).
			return Ordered.HIGHEST_PRECEDENCE + 5;
		}

		@Override
		public void initialize(ConfigurableApplicationContext context) {
			//如果存在父级，找到当前上下文的父级上下文，否则是当前上下文
			while (context.getParent() != null && context.getParent() != context) {
				context = (ConfigurableApplicationContext) context.getParent();
			}
			//重新排序属性
			reorderSources(context.getEnvironment());
			//代理给ParentContextApplicationContextInitializer进行处理
			//1、设置context的parent为启动上下文
			//2、给context增加一个EventPublisher，接受ContextRefreshedEvent事件，发送ParentContextAvailableEvent事件
			new ParentContextApplicationContextInitializer(this.parent).initialize(context);
		}

		/**
		 * 重新排期属性，添加后系统将不存在ExtendedDefaultPropertySource的属性
		 *
		 * @param environment 当前环境或者是父级环境
		 */
		private void reorderSources(ConfigurableEnvironment environment) {
			//移除名为springCloudDefaultProperties的PropertySource
			PropertySource<?> removed = environment.getPropertySources().remove(DEFAULT_PROPERTIES);
			if (removed instanceof ExtendedDefaultPropertySource) {
				ExtendedDefaultPropertySource defaultProperties = (ExtendedDefaultPropertySource) removed;
				//添加名为名为springCloudDefaultProperties的MapPropertySource
				//添加至最后
				environment.getPropertySources()
						.addLast(new MapPropertySource(DEFAULT_PROPERTIES, defaultProperties.getSource()));
				//获取复合属性列表
				for (PropertySource<?> source : defaultProperties.getPropertySources().getPropertySources()) {
					//环境中没有这个属性
					if (!environment.getPropertySources().contains(source.getName())) {
						//添加至springCloudDefaultProperties之前
						environment.getPropertySources().addBefore(DEFAULT_PROPERTIES, source);
					}
				}
			}
		}

	}

	/**
	 * A special initializer designed to run before the property source bootstrap and
	 * decrypt any properties needed there (e.g. URL of config server).
	 */
	@Order(Ordered.HIGHEST_PRECEDENCE + 9)
	private static class DelegatingEnvironmentDecryptApplicationInitializer
			implements ApplicationContextInitializer<ConfigurableApplicationContext> {

		private ApplicationContextInitializer<ConfigurableApplicationContext> delegate;

		DelegatingEnvironmentDecryptApplicationInitializer(
				ApplicationContextInitializer<ConfigurableApplicationContext> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void initialize(ConfigurableApplicationContext applicationContext) {
			this.delegate.initialize(applicationContext);
		}

	}

	/**
	 * 扩展的默认属性
	 */
	private static class ExtendedDefaultPropertySource extends SystemEnvironmentPropertySource implements OriginLookup<String> {

		/**
		 * 用于跟踪原始属性
		 */
		private final OriginTrackedCompositePropertySource sources;

		private final List<String> names = new ArrayList<>();

		ExtendedDefaultPropertySource(String name, PropertySource<?> propertySource) {
			//设置map类型的属性
			super(name, findMap(propertySource));
			this.sources = new OriginTrackedCompositePropertySource(name);
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> findMap(PropertySource<?> propertySource) {
			if (propertySource instanceof MapPropertySource) {
				return (Map<String, Object>) propertySource.getSource();
			}
			return new LinkedHashMap<>();
		}

		public CompositePropertySource getPropertySources() {
			return this.sources;
		}

		public List<String> getPropertySourceNames() {
			return this.names;
		}

		//添加属性，添加进OriginTrackedCompositePropertySource中
		//添加类型是OriginTrackedMapPropertySource
		public void add(PropertySource<?> source) {
			// Only add map property sources added by boot, see gh-476
			//只添加OriginTrackedMapPropertySource类型属性
			if (source instanceof OriginTrackedMapPropertySource && !this.names.contains(source.getName())) {
				this.sources.addPropertySource(source);
				this.names.add(source.getName());
			}
		}

		@Override
		public Object getProperty(String name) {
			if (this.sources.containsProperty(name)) {
				return this.sources.getProperty(name);
			}
			return super.getProperty(name);
		}

		@Override
		public boolean containsProperty(String name) {
			if (this.sources.containsProperty(name)) {
				return true;
			}
			return super.containsProperty(name);
		}

		@Override
		public String[] getPropertyNames() {
			List<String> names = new ArrayList<>();
			names.addAll(Arrays.asList(this.sources.getPropertyNames()));
			names.addAll(Arrays.asList(super.getPropertyNames()));
			return names.toArray(new String[0]);
		}

		@Override
		public Origin getOrigin(String name) {
			return this.sources.getOrigin(name);
		}

	}

	private static class CloseContextOnFailureApplicationListener implements SmartApplicationListener {

		private final ConfigurableApplicationContext context;

		CloseContextOnFailureApplicationListener(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
			return ApplicationFailedEvent.class.isAssignableFrom(eventType);
		}

		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			if (event instanceof ApplicationFailedEvent) {
				this.context.close();
			}

		}

	}

}
