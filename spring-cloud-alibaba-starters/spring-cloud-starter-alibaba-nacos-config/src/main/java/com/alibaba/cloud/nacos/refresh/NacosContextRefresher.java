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

package com.alibaba.cloud.nacos.refresh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.alibaba.cloud.nacos.client.NacosPropertySource;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

/**
 * On application start up, NacosContextRefresher add nacos listeners to all application
 * level dataIds, when there is a change in the data, listeners will refresh
 * configurations.
 *
 * @author juven.xuxb
 * @author pbting
 * <p>
 * TODO 因为 实现了ApplicationListener<ApplicationReadyEvent>接口 看 onApplicationEvent方法
 * <p>
 * TODO 这个类的加载是由NacosConfigAutoConfiguration
 */
public class NacosContextRefresher
        implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {

    private final static Logger log = LoggerFactory
            .getLogger(NacosContextRefresher.class);

    private static final AtomicLong REFRESH_COUNT = new AtomicLong(0);
    private final boolean isRefreshEnabled;
    private final NacosRefreshHistory nacosRefreshHistory;
    private NacosConfigProperties nacosConfigProperties;
    private ConfigService configService;

    private NacosConfigManager configManager;

    private ApplicationContext applicationContext;

    private AtomicBoolean ready = new AtomicBoolean(false);

    private Map<String, Listener> listenerMap = new ConcurrentHashMap<>(16);

    public NacosContextRefresher(NacosConfigManager nacosConfigManager,
                                 NacosRefreshHistory refreshHistory) {
        this.configManager = nacosConfigManager;
        this.nacosConfigProperties = nacosConfigManager.getNacosConfigProperties();
        this.nacosRefreshHistory = refreshHistory;
        this.isRefreshEnabled = this.nacosConfigProperties.isRefreshEnabled();
    }

    /**
     * recommend to use
     * {@link NacosContextRefresher#NacosContextRefresher(NacosConfigManager, NacosRefreshHistory)}.
     *
     * @param refreshProperties refreshProperties
     * @param refreshHistory    refreshHistory
     * @param configService     configService
     */
    @Deprecated
    public NacosContextRefresher(NacosRefreshProperties refreshProperties,
                                 NacosRefreshHistory refreshHistory, ConfigService configService) {
        this.isRefreshEnabled = refreshProperties.isEnabled();
        this.nacosRefreshHistory = refreshHistory;
        this.configService = configService;
    }

    public static long getRefreshCount() {
        return REFRESH_COUNT.get();
    }

    public static void refreshCountIncrement() {
        REFRESH_COUNT.incrementAndGet();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // many Spring context
        //ready 原子类 默认 false  进行 CAS 更新
        if (this.ready.compareAndSet(false, true)) {
            // 注册监听
            this.registerNacosListenersForApplications();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * register Nacos Listeners.
     * TODO 重点
     * <p>
     * TODO NacosContextRefresher#registerNacosListenersForApplications
     *          方法会判断是否启动了自动刷新配置，然后获取所有的 Nacos 配置，
     *          并判断每个配置是否可以刷新，如果可以刷新则注册响应的监听。
     */
    private void registerNacosListenersForApplications() {
        // 是否开启配置刷新 spring.cloud.nacos.config.refreshEnabled属性的值为true, 表示开启配置刷新
        if (isRefreshEnabled()) {
            // 这个就是从缓存中拿到之前获取完配置内容后，封装成NacosPropertySource之后，缓存起来的内容
            // ConcurrentHashMap<String, NacosPropertySource> NACOS_PROPERTY_SOURCE_REPOSITORY
            for (NacosPropertySource propertySource : NacosPropertySourceRepository
                    .getAll()) {
                if (!propertySource.isRefreshable()) {
                    continue;
                }
                // 获取配置dataId
                String dataId = propertySource.getDataId();
                // 注册nacos配置监听器
                // TODO 进入
                registerNacosListener(propertySource.getGroup(), dataId);
            }
        }
    }

    /**
     * TODO 和 client端的com.alibaba.nacos.client.config.impl.CacheData#safeNotifyListener联动
     * TODO NacosContextRefresher#registerNacosListener 方法主要就是注册监听器。
     *          其主要操作就是刷新记录加一，
     *          增加 Nacos 刷新记录，
     *          发布 RefreshEvent 事件，
     *          然后调用 NacosConfigService#addListener 注册监听器。
     * @param groupKey
     * @param dataKey
     */
    private void registerNacosListener(final String groupKey, final String dataKey) {
        // key = {dataId,group}
        String key = NacosPropertySourceRepository.getMapKey(dataKey, groupKey);
        // 这里声明了一个监听器，注意这只是声明，后续配置发生变化时，会回调到这里。
        // 监听采用的回调的思想，当服务端通知的时候，调用回调方法
        Listener listener = listenerMap.computeIfAbsent(key,
                lst -> new AbstractSharedListener() {
                    @Override
                    public void innerReceive(String dataId, String group,
                                             String configInfo) {
                        // 累加配置刷新次数
                        refreshCountIncrement();
                        // 添加一条刷新记录
                        nacosRefreshHistory.addRefreshRecord(dataId, group, configInfo);
                        // 通过Spring上下文发布一个RefreshEvent刷新事件
                        NacosSnapshotConfigManager.putConfigSnapshot(dataId, group,
                                configInfo);
                        applicationContext.publishEvent(
                                new RefreshEvent(this, null, "Refresh Nacos config"));
                        if (log.isDebugEnabled()) {
                            log.debug(String.format(
                                    "Refresh Nacos config group=%s,dataId=%s,configInfo=%s",
                                    group, dataId, configInfo));
                        }
                    }
                });
        try {
            if (configService == null && configManager != null) {
                configService = configManager.getConfigService();
            }
            // TODO  注册配置监听器，以 dataId + groupId + namespace 为维度进行注册的
            // TODO  进入 看nacos client代码
            configService.addListener(dataKey, groupKey, listener);
        } catch (NacosException e) {
            log.warn(String.format(
                    "register fail for nacos listener ,dataId=[%s],group=[%s]", dataKey,
                    groupKey), e);
        }
    }

    public NacosConfigProperties getNacosConfigProperties() {
        return nacosConfigProperties;
    }

    public NacosContextRefresher setNacosConfigProperties(
            NacosConfigProperties nacosConfigProperties) {
        this.nacosConfigProperties = nacosConfigProperties;
        return this;
    }

    public boolean isRefreshEnabled() {
        if (null == nacosConfigProperties) {
            return isRefreshEnabled;
        }
        // Compatible with older configurations
        if (nacosConfigProperties.isRefreshEnabled() && !isRefreshEnabled) {
            return false;
        }
        return isRefreshEnabled;
    }

}
