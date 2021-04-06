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

package org.springframework.cloud.bootstrap.support;

import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 用于跟踪原始属性，继承自复合属性，复合属性是指一个属性内部保存了多个属性的集合
 *
 * @author qings
 */
public class OriginTrackedCompositePropertySource extends CompositePropertySource implements OriginLookup<String> {

	/**
	 * Create a new {@code CompositePropertySource}.
	 *
	 * @param name the name of the property source
	 */
	public OriginTrackedCompositePropertySource(String name) {
		super(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Origin getOrigin(String name) {
		//获取复合属性中所有的属性
		for (PropertySource<?> propertySource : getPropertySources()) {
			//如果是OriginLookup类型
			if (propertySource instanceof OriginLookup) {
				OriginLookup lookup = (OriginLookup) propertySource;
				Origin origin = lookup.getOrigin(name);
				if (origin != null) {
					return origin;
				}
			}
		}
		return null;
	}

}
