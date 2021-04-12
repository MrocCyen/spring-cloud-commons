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

package org.springframework.cloud.client.loadbalancer;

import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 */

/**
 * RestTemplate自定义器
 */
//todo 用户需要实现
//springcloud实现的是给RestTemplate注入拦截器
public interface RestTemplateCustomizer {

	void customize(RestTemplate restTemplate);

}
