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

package org.springframework.cloud.context.properties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Collects references to <code>@ConfigurationProperties</code> beans in the context and
 * its parent.
 *
 * @author Dave Syer
 */

/**
 * 收集被@ConfigurationProperties注解了的bean
 */
@Component
public class ConfigurationPropertiesBeans implements BeanPostProcessor, ApplicationContextAware {

	//如果当前的context存在父级，则在setApplicationContext方法中将父级的beans进行赋值
	private Map<String, ConfigurationPropertiesBean> beans = new HashMap<>();

	//setApplicationContext方法中赋值
	private ApplicationContext applicationContext;

	//setApplicationContext方法中赋值
	private ConfigurableListableBeanFactory beanFactory;

	private String refreshScope;

	private boolean refreshScopeInitialized;

	//setApplicationContext方法中赋值
	private ConfigurationPropertiesBeans parent;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		if (applicationContext.getAutowireCapableBeanFactory() instanceof ConfigurableListableBeanFactory) {
			this.beanFactory = (ConfigurableListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
		}
		if (applicationContext.getParent() != null && applicationContext.getParent()
				.getAutowireCapableBeanFactory() instanceof ConfigurableListableBeanFactory) {
			ConfigurableListableBeanFactory listable = (ConfigurableListableBeanFactory) applicationContext.getParent()
					.getAutowireCapableBeanFactory();
			//从父级context获取ConfigurationPropertiesBeans
			String[] names = listable.getBeanNamesForType(ConfigurationPropertiesBeans.class);
			//按道理，父级只有一个ConfigurationPropertiesBeans，一般开发者都不会去定义这个类
			if (names.length == 1) {
				//设置父级ConfigurationPropertiesBeans
				this.parent = (ConfigurationPropertiesBeans) listable.getBean(names[0]);
				//保存父级的ConfigurationPropertiesBeans到本级
				this.beans.putAll(this.parent.beans);
			}
		}
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		//如果是RefreshScope类型的bean，则直接忽略
		if (isRefreshScoped(beanName)) {
			return bean;
		}
		//找出当前context中的ConfigurationPropertiesBean
		ConfigurationPropertiesBean propertiesBean = ConfigurationPropertiesBean.get(this.applicationContext, bean,
				beanName);
		if (propertiesBean != null) {
			this.beans.put(beanName, propertiesBean);
		}
		return bean;
	}

	/**
	 * 判断是否是refresh作用域，找出RefreshScope，并赋值
	 *
	 * @param beanName bean名称
	 * @return
	 */
	private boolean isRefreshScoped(String beanName) {
		//第一次可以直接进入，找出RefreshScope，并赋值
		if (this.refreshScope == null && !this.refreshScopeInitialized) {
			this.refreshScopeInitialized = true;
			for (String scope : this.beanFactory.getRegisteredScopeNames()) {
				if (this.beanFactory.getRegisteredScope(
						scope) instanceof org.springframework.cloud.context.scope.refresh.RefreshScope) {
					//找出RefreshScope，并赋值
					this.refreshScope = scope;
					break;
				}
			}
		}
		if (beanName == null || this.refreshScope == null) {
			return false;
		}
		return this.beanFactory.containsBeanDefinition(beanName)
				&& this.refreshScope.equals(this.beanFactory.getBeanDefinition(beanName).getScope());
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	public Set<String> getBeanNames() {
		return new HashSet<String>(this.beans.keySet());
	}

}
