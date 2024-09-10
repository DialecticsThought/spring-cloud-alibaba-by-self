/*
 * Copyright 2013-2018 the original author or authors.
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

package com.alibaba.cloud.dubbo.service;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link DubboMetadataService} exporter.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
@Component
public class DubboMetadataServiceExporter {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ApplicationConfig applicationConfig;

	@Autowired
	private ObjectProvider<DubboMetadataService> dubboMetadataService;

	@Autowired
	private Supplier<ProtocolConfig> protocolConfigSupplier;

	@Value("${spring.application.name:${dubbo.application.name:application}}")
	private String currentApplicationName;

	/**
	 * The ServiceConfig of DubboMetadataConfigService to be exported, can be nullable.
	 */
	private ServiceConfig<DubboMetadataService> serviceConfig;

	/**
	 * export {@link DubboMetadataService} as Dubbo service.
	 * @return the exported {@link URL URLs}
	 */
	// 导出最新的元数据
	public List<URL> export() {
		// 如果ServiceConfig类已经被初始化 并且已经导出完毕 则直接返回
		if (serviceConfig == null || !serviceConfig.isExported()) {

			serviceConfig = new ServiceConfig<>();
			// 设置接口为DubboMetatdataService
			serviceConfig.setInterface(DubboMetadataService.class);
			// 设置元数据的版本号
			// Use DubboMetadataService.VERSION as the Dubbo Service version
			serviceConfig.setVersion(DubboMetadataService.VERSION);
			// 设置元数据组名为当前应用名称
			// Use current Spring application name as the Dubbo Service group
			serviceConfig.setGroup(currentApplicationName);
			// 设置元数据独对应的实例的可用性
			serviceConfig.setRef(dubboMetadataService.getIfAvailable());
			// 设置全局应用配置信息
			serviceConfig.setApplication(applicationConfig);
			// 导出元数据
			serviceConfig.setProtocol(protocolConfigSupplier.get());

			serviceConfig.export();

			if (logger.isInfoEnabled()) {
				logger.info("The Dubbo service[{}] has been exported.",
						serviceConfig.toString());
			}
		}

		return serviceConfig.getExportedUrls();
	}

	/**
	 * unexport {@link DubboMetadataService}.
	 */
	@PreDestroy
	public void unexport() {

		if (serviceConfig == null || serviceConfig.isUnexported()) {
			return;
		}

		serviceConfig.unexport();

		if (logger.isInfoEnabled()) {
			logger.info("The Dubbo service[{}] has been unexported.",
					serviceConfig.toString());
		}
	}

}
