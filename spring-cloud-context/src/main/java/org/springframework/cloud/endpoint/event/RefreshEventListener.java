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

package org.springframework.cloud.endpoint.event;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Calls {@link ContextRefresher#refresh} when a {@link RefreshEvent} is received. Only
 * responds to {@link RefreshEvent} after receiving an {@link ApplicationReadyEvent}, as
 * the RefreshEvents might come too early in the application lifecycle.
 *
 * @author Spencer Gibb
 */
public class RefreshEventListener implements SmartApplicationListener {

	private static Log log = LogFactory.getLog(RefreshEventListener.class);

	//想下文刷新器
	private ContextRefresher refresh;

	//标志环境是否已经准备好了
	private AtomicBoolean ready = new AtomicBoolean(false);

	public RefreshEventListener(ContextRefresher refresh) {
		this.refresh = refresh;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		//只支持ApplicationReadyEvent和RefreshEvent事件
		return ApplicationReadyEvent.class.isAssignableFrom(eventType)
				|| RefreshEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		//分别处理ApplicationReadyEvent和RefreshEvent事件
		if (event instanceof ApplicationReadyEvent) {
			handle((ApplicationReadyEvent) event);
		} else if (event instanceof RefreshEvent) {
			handle((RefreshEvent) event);
		}
	}

	public void handle(ApplicationReadyEvent event) {
		//直接设置属性值为true
		this.ready.compareAndSet(false, true);
	}

	public void handle(RefreshEvent event) {
		// don't handle events before app is ready
		//必须环境已经准备好了才进行处理RefreshEvent事件
		if (this.ready.get()) {
			log.debug("Event received " + event.getEventDesc());
			//刷新上下文
			Set<String> keys = this.refresh.refresh();
			log.info("Refresh keys changed: " + keys);
		}
	}

}
