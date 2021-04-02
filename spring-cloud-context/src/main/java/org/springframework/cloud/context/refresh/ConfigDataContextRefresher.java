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

import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * @author Dave Syer
 * @author Venil Noronha
 */
public class ConfigDataContextRefresher extends ContextRefresher {

	@Deprecated
	public ConfigDataContextRefresher(ConfigurableApplicationContext context, RefreshScope scope) {
		super(context, scope);
	}

	public ConfigDataContextRefresher(ConfigurableApplicationContext context, RefreshScope scope,
									  RefreshAutoConfiguration.RefreshProperties properties) {
		super(context, scope, properties);
	}

	@Override
	protected void updateEnvironment() {
		if (logger.isTraceEnabled()) {
			logger.trace("Re-processing environment to add config data");
		}
		//按照当前的环境复制一份
		StandardEnvironment environment = copyEnvironment(getContext().getEnvironment());
		//获取当前环境的profiles
		String[] activeProfiles = getContext().getEnvironment().getActiveProfiles();
		//todo 应用环境 ConfigDataEnvironmentPostProcessor这个类需要好好看看
		ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), new DefaultBootstrapContext(),
				activeProfiles);
		//移除名为refreshArgs的属性PropertySources
		if (environment.getPropertySources().contains(REFRESH_ARGS_PROPERTY_SOURCE)) {
			environment.getPropertySources().remove(REFRESH_ARGS_PROPERTY_SOURCE);
		}
		//原始context中环境的属性
		MutablePropertySources target = getContext().getEnvironment().getPropertySources();
		String targetName = null;
		//更新后的环境中的属性
		for (PropertySource<?> source : environment.getPropertySources()) {
			String name = source.getName();
			//如果原始环境中有这个属性集合，赋值targetName
			if (target.contains(name)) {
				targetName = name;
			}
			//不是系统标准的环境属性
			if (!this.standardSources.contains(name)) {
				//如果原始环境中有这个属性集合
				if (target.contains(name)) {
					//替换原始环境中的属性
					target.replace(name, source);
				} else {//如果原始环境中没有这个属性集合
					if (targetName != null) {
						target.addAfter(targetName, source);
						// update targetName to preserve ordering
						targetName = name;
					} else {
						// targetName was null so we are at the start of the list
						target.addFirst(source);
						targetName = name;
					}
				}
			}
		}
	}

}
