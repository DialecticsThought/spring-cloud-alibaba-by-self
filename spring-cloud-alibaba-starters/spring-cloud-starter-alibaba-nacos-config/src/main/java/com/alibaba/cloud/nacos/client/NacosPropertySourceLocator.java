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

package com.alibaba.cloud.nacos.client;

import java.util.List;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.alibaba.cloud.nacos.parser.NacosDataParserHandler;
import com.alibaba.cloud.nacos.refresh.NacosContextRefresher;
import com.alibaba.nacos.api.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author xiaojing
 * @author pbting
 * 在项目启动的时候（上下文准备阶段）通过NacosPropertySourceLocator就拉取到了远程 Nacos 中的配置信息，并且封装成 NacosPropertySource对象
 * PropertySourceBootstrapConfiguration依靠ApplicationContextInitializer机制（容器刷新之前支持一些自定义初始化工作），
 * 将前面封装好的NacosPropertySource对象放到了 Spring 的环境变量Environment 中
 */
@Order(0)
public class NacosPropertySourceLocator implements PropertySourceLocator {

	private static final Logger log = LoggerFactory
			.getLogger(NacosPropertySourceLocator.class);

	private static final String NACOS_PROPERTY_SOURCE_NAME = "NACOS";

	private static final String SEP1 = "-";

	private static final String DOT = ".";

	private NacosPropertySourceBuilder nacosPropertySourceBuilder;

	private NacosConfigProperties nacosConfigProperties;

	private NacosConfigManager nacosConfigManager;

	/**
	 * recommend to use
	 * {@link NacosPropertySourceLocator#NacosPropertySourceLocator(com.alibaba.cloud.nacos.NacosConfigManager)}.
	 * @param nacosConfigProperties nacosConfigProperties
	 */
	@Deprecated
	public NacosPropertySourceLocator(NacosConfigProperties nacosConfigProperties) {
		this.nacosConfigProperties = nacosConfigProperties;
	}

	public NacosPropertySourceLocator(NacosConfigManager nacosConfigManager) {
		this.nacosConfigManager = nacosConfigManager;
		this.nacosConfigProperties = nacosConfigManager.getNacosConfigProperties();
	}

	@Override
	public PropertySource<?> locate(Environment env) {
		nacosConfigProperties.setEnvironment(env);
		// 通过反射创建出一个NacosConfigService实例
		// TODO 进入方法
		// NacosConfigService 是一个很核心的类，配置的获取，监听器的注册都需要经此
		// 查看  NacosConfigService 初始化方法 因为 会创建ClientWorker 查看client
		ConfigService configService = nacosConfigManager.getConfigService();

		if (null == configService) {
			log.warn("no instance of config service found, can't load config from nacos");
			return null;
		}
		long timeout = nacosConfigProperties.getTimeout();
		// 配置获取（使用 configService）、配置封装、配置缓存等操作
		nacosPropertySourceBuilder = new NacosPropertySourceBuilder(configService,
				timeout);
		String name = nacosConfigProperties.getName();

		String dataIdPrefix = nacosConfigProperties.getPrefix();
		if (StringUtils.isEmpty(dataIdPrefix)) {
			dataIdPrefix = name;
		}

		if (StringUtils.isEmpty(dataIdPrefix)) {
			dataIdPrefix = env.getProperty("spring.application.name");
		}

		CompositePropertySource composite = new CompositePropertySource(
				NACOS_PROPERTY_SOURCE_NAME);
		// 加载共享的配置信息
		// TODO 进入
		loadSharedConfiguration(composite);
		// 加载扩展的配置信息
		//TODO 进入
		loadExtConfiguration(composite);
		// 加载应用自身的配置信息
		//TODO 进入
		loadApplicationConfiguration(composite, dataIdPrefix, nacosConfigProperties, env);
		return composite;
	}

	/**
	 * load shared configuration.
	 */
	private void loadSharedConfiguration(
			CompositePropertySource compositePropertySource) {
		// 获取共享配置
		List<NacosConfigProperties.Config> sharedConfigs = nacosConfigProperties
				.getSharedConfigs();
		if (!CollectionUtils.isEmpty(sharedConfigs)) {
			checkConfiguration(sharedConfigs, "shared-configs");
			// 加载配置
			//TODO 进入
			loadNacosConfiguration(compositePropertySource, sharedConfigs);
		}
	}

	/**
	 * load extensional configuration.
	 */
	private void loadExtConfiguration(CompositePropertySource compositePropertySource) {
		// 获取扩展配置
		List<NacosConfigProperties.Config> extConfigs = nacosConfigProperties
				.getExtensionConfigs();
		if (!CollectionUtils.isEmpty(extConfigs)) {
			checkConfiguration(extConfigs, "extension-configs");
			// 获取扩展配置
			loadNacosConfiguration(compositePropertySource, extConfigs);
		}
	}

	/**
	 * load configuration of application.
	 * 同时会加载以下三种配置，分别是
	 * 1、不带扩展名后缀查询，application
	 * 2、带扩展名后缀查询，application.yml
	 * 3、带环境，带扩展名后缀查询，application-prod.yml
	 */
	private void loadApplicationConfiguration(
			CompositePropertySource compositePropertySource, String dataIdPrefix,
			NacosConfigProperties properties, Environment environment) {
		// 配置文件扩展名
		String fileExtension = properties.getFileExtension();
		// 配置组名
		String nacosGroup = properties.getGroup();
		// load directly once by default
		// 不带扩展名后缀查询，application
		//TODO 进入 核心
		loadNacosDataIfPresent(compositePropertySource, dataIdPrefix, nacosGroup,
				fileExtension, true);
		// load with suffix, which have a higher priority than the default
		// 带扩展名后缀查询，application.yml
		loadNacosDataIfPresent(compositePropertySource,
				dataIdPrefix + DOT + fileExtension, nacosGroup, fileExtension, true);
		// Loaded with profile, which have a higher priority than the suffix
		// 带环境，带扩展名后缀查询，application-prod.yml
		for (String profile : environment.getActiveProfiles()) {
			String dataId = dataIdPrefix + SEP1 + profile + DOT + fileExtension;
			loadNacosDataIfPresent(compositePropertySource, dataId, nacosGroup,
					fileExtension, true);
		}

	}

	private void loadNacosConfiguration(final CompositePropertySource composite,
			List<NacosConfigProperties.Config> configs) {
		for (NacosConfigProperties.Config config : configs) {
			String fileExtension = config.getFileExtension();
			if (StringUtils.isEmpty(fileExtension)) {
				fileExtension = NacosDataParserHandler.getInstance()
						.getFileExtension(config.getDataId());
			}
			loadNacosDataIfPresent(composite, config.getDataId(), config.getGroup(),
					fileExtension, config.isRefresh());
		}
	}

	private void checkConfiguration(List<NacosConfigProperties.Config> configs,
			String tips) {
		for (int i = 0; i < configs.size(); i++) {
			String dataId = configs.get(i).getDataId();
			if (dataId == null || dataId.trim().length() == 0) {
				throw new IllegalStateException(String.format(
						"the [ spring.cloud.nacos.config.%s[%s] ] must give a dataId",
						tips, i));
			}
		}
	}

	private void loadNacosDataIfPresent(final CompositePropertySource composite,
			final String dataId, final String group, String fileExtension,
			boolean isRefreshable) {
		if (null == dataId || dataId.trim().length() < 1) {
			return;
		}
		if (null == group || group.trim().length() < 1) {
			return;
		}
		//TODO 进入 核心
		NacosPropertySource propertySource = this.loadNacosPropertySource(dataId, group,
				fileExtension, isRefreshable);
		this.addFirstPropertySource(composite, propertySource, false);
	}

	private NacosPropertySource loadNacosPropertySource(final String dataId,
			final String group, String fileExtension, boolean isRefreshable) {
		if (NacosContextRefresher.getRefreshCount() != 0) {
			if (!isRefreshable) {
				return NacosPropertySourceRepository.getNacosPropertySource(dataId,
						group);
			}
		}
		//TODO 进入 核心
		// nacosPropertySourceBuilder.build()方法调用 loadNacosData 获取配置，然后封装成 NacosPropertySource，
		// 并且将该对象缓存到 NacosPropertySourceRepository中，后续会用到。
		return nacosPropertySourceBuilder.build(dataId, group, fileExtension,
				isRefreshable);
	}

	/**
	 * Add the nacos configuration to the first place and maybe ignore the empty
	 * configuration.
	 */
	private void addFirstPropertySource(final CompositePropertySource composite,
			NacosPropertySource nacosPropertySource, boolean ignoreEmpty) {
		if (null == nacosPropertySource || null == composite) {
			return;
		}
		if (ignoreEmpty && nacosPropertySource.getSource().isEmpty()) {
			return;
		}
		composite.addFirstPropertySource(nacosPropertySource);
	}

	public void setNacosConfigManager(NacosConfigManager nacosConfigManager) {
		this.nacosConfigManager = nacosConfigManager;
	}

}
