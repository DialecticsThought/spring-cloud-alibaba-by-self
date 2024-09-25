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

package com.alibaba.cloud.nacos;

import com.alibaba.cloud.nacos.refresh.NacosContextRefresher;
import com.alibaba.cloud.nacos.refresh.NacosRefreshHistory;
import com.alibaba.cloud.nacos.refresh.NacosRefreshProperties;
import com.alibaba.cloud.nacos.refresh.SmartConfigurationPropertiesRebinder;
import com.alibaba.cloud.nacos.refresh.condition.ConditionalOnNonDefaultBehavior;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.cloud.context.properties.ConfigurationPropertiesBeans;
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author juven.xuxb
 * @author freeman
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.nacos.config.enabled", matchIfMissing = true)
public class NacosConfigAutoConfiguration {

    /**
     * 作用：封装 Nacos 配置的属性，如服务地址、命名空间等。
     * 用途：允许在应用程序中访问 Nacos 配置的各种设置，通过注入该 Bean，可以轻松获取和管理 Nacos 的配置
     * @param context
     * @return
     */
    @Bean
    public NacosConfigProperties nacosConfigProperties(ApplicationContext context) {
        if (context.getParent() != null
                && BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                context.getParent(), NacosConfigProperties.class).length > 0) {
            return BeanFactoryUtils.beanOfTypeIncludingAncestors(context.getParent(),
                    NacosConfigProperties.class);
        }
        return new NacosConfigProperties();
    }

    /**
     * 作用：管理 Nacos 配置的刷新策略，例如是否自动刷新配置。
     * 用途：提供有关如何处理配置更新的配置选项，帮助应用决定何时刷新配置。
     * @return
     */
    @Bean
    public NacosRefreshProperties nacosRefreshProperties() {
        return new NacosRefreshProperties();
    }

    /**
     * 作用：记录配置刷新的历史信息。
     * 用途：跟踪配置变化，便于后续分析和调试配置变更的影响。
     * @return
     */
    @Bean
    public NacosRefreshHistory nacosRefreshHistory() {
        return new NacosRefreshHistory();
    }

    /**
     * 作用：管理 Nacos 配置的生命周期，包括获取和更新配置的功能。
     * 用途：提供对 Nacos 配置的集中管理，使其他组件可以通过它获取当前的配置状态
     * @param nacosConfigProperties
     * @return
     */
    @Bean
    public NacosConfigManager nacosConfigManager(
            NacosConfigProperties nacosConfigProperties) {
        return new NacosConfigManager(nacosConfigProperties);
    }

    /**
     * TODO 重点
     * 作用：处理上下文的刷新逻辑。
     * 用途：当 Nacos 配置发生变化时，自动更新 Spring 上下文中的相应 Bean，实现动态配置更新
     * @param nacosConfigManager
     * @param nacosRefreshHistory
     * @return
     */
    @Bean
    public NacosContextRefresher nacosContextRefresher(
            NacosConfigManager nacosConfigManager,
            NacosRefreshHistory nacosRefreshHistory) {
        // Consider that it is not necessary to be compatible with the previous
        // configuration
        // and use the new configuration if necessary.
        // TODO 进入
        return new NacosContextRefresher(nacosConfigManager, nacosRefreshHistory);
    }

    /**
     * 作用：支持动态更新的配置属性重绑定器。
     * 用途：在配置更新后，自动将新的属性值重新绑定到相应的 Bean 上，以确保应用程序使用最新的配置
     * @param beans
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
    @ConditionalOnNonDefaultBehavior
    public ConfigurationPropertiesRebinder smartConfigurationPropertiesRebinder(
            ConfigurationPropertiesBeans beans) {
        // If using default behavior, not use SmartConfigurationPropertiesRebinder.
        // Minimize te possibility of making mistakes.
        return new SmartConfigurationPropertiesRebinder(beans);
    }

}
