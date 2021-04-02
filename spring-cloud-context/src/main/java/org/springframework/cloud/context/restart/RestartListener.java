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

package org.springframework.cloud.context.restart;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * A listener that stores enough information about an application, as it starts, to be
 * able to restart it later if needed.
 *
 * @author Dave Syer
 */
public class RestartListener implements SmartApplicationListener {

	//上下人
	private ConfigurableApplicationContext context;

	//应用程序准备事件
	private ApplicationPreparedEvent event;

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationPreparedEvent.class.isAssignableFrom(eventType)
				|| ContextRefreshedEvent.class.isAssignableFrom(eventType)
				|| ContextClosedEvent.class.isAssignableFrom(eventType);

	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	//在springboot中，ApplicationPreparedEvent事件先与ContextRefreshedEvent事件发布
	//ContextRefreshedEvent事件是在context的refresh方法中进行的
	@Override
	public void onApplicationEvent(ApplicationEvent input) {
		//程序第一次进入，保存event为ApplicationPreparedEvent
		//或者会是下面的分支发布的ApplicationPreparedEvent事件
		if (input instanceof ApplicationPreparedEvent) {
			//每次产生ApplicationPreparedEvent后，都进行保存
			this.event = (ApplicationPreparedEvent) input;
			//这里只保存第一次保存的context，防止其他context进行赋值
			if (this.context == null) {
				this.context = this.event.getApplicationContext();
			}
		} else if (input instanceof ContextRefreshedEvent) {
			//接下来程序refresh，发布ContextRefreshedEvent事件
			//如果是当前的context，则进行事件发布
			if (this.context != null && input.getSource().equals(this.context) && this.event != null) {
				//发布保存的ApplicationPreparedEvent事件
				this.context.publishEvent(this.event);
			}
		} else {
			//当前容器关闭，置空event和context
			if (this.context != null && input.getSource().equals(this.context)) {
				this.context = null;
				this.event = null;
			}
		}
	}

}
