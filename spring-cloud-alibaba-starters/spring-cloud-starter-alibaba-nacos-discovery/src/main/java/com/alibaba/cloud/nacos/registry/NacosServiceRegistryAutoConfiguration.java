/*
 * Copyright 2013-2023 the original author or authors.
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

package com.alibaba.cloud.nacos.registry;

import java.util.List;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationConfiguration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xiaojing
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConditionalOnNacosDiscoveryEnabled
@ConditionalOnProperty(value = "spring.cloud.service-registry.auto-registration.enabled",
        matchIfMissing = true)
@AutoConfigureAfter({AutoServiceRegistrationConfiguration.class,
        AutoServiceRegistrationAutoConfiguration.class,
        NacosDiscoveryAutoConfiguration.class})
public class NacosServiceRegistryAutoConfiguration {

	/**
	 * TODO 查看 该类 和父类
	 * @param nacosServiceManager
	 * @param nacosDiscoveryProperties
	 * @return
	 */
    @Bean
    public NacosServiceRegistry nacosServiceRegistry(
            NacosServiceManager nacosServiceManager,
            NacosDiscoveryProperties nacosDiscoveryProperties) {//TODO 查看 NacosDiscoveryProperties
        return new NacosServiceRegistry(nacosServiceManager, nacosDiscoveryProperties);
    }

    @Bean
    @ConditionalOnBean(AutoServiceRegistrationProperties.class)
    public NacosRegistration nacosRegistration(
            ObjectProvider<List<NacosRegistrationCustomizer>> registrationCustomizers,
            NacosDiscoveryProperties nacosDiscoveryProperties,
            ApplicationContext context) {
        return new NacosRegistration(registrationCustomizers.getIfAvailable(),
                nacosDiscoveryProperties, context);
    }

    /**
     * 这个Bean在创建时，会传入NacosServiceRegistry和NacosRegistration两个Bean。
     * 然后该Bean继承了AbstractAutoServiceRegistration抽象类。该抽象类实现了ApplicationListener接口，
     * 所以项目启动时便是利用了Spring的监听事件来实现自动注册服务的。
     * 因为在Spring容器启动的最后会执行finishRefresh()方法，然后会发布一个事件，该事件会触发调用onApplicationEvent()方法
     * <p>
     * 调用AbstractAutoServiceRegistration的onApplicationEvent()方法时，
     * 首先会调用AbstractAutoServiceRegistration的bind()方法，
     * 然后调用AbstractAutoServiceRegistration的start()方法，
     * 接着调用AbstractAutoServiceRegistration的register()方法发起注册，
     * 也就是调用this.serviceRegistry的register()方法完成服务注册的具体工作。
     *
     * @param registry
     * @param autoServiceRegistrationProperties
     * @param registration
     * @return
     */
    @Bean
    @ConditionalOnBean(AutoServiceRegistrationProperties.class)
    public NacosAutoServiceRegistration nacosAutoServiceRegistration(
            NacosServiceRegistry registry,
            AutoServiceRegistrationProperties autoServiceRegistrationProperties,
            NacosRegistration registration) {
        return new NacosAutoServiceRegistration(registry,
                autoServiceRegistrationProperties, registration);
    }

}
