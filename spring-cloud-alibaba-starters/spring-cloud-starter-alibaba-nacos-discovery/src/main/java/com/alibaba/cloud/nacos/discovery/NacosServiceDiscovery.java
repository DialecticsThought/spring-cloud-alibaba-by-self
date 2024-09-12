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

package com.alibaba.cloud.nacos.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceInstance;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;

import org.springframework.cloud.client.ServiceInstance;

/**
 * @author <a href="mailto:echooy.mxq@gmail.com">echooymxq</a>
 **/
public class NacosServiceDiscovery {
    // 跟配置文件中以“spring.cloud.nacos.discovery”前缀的属性配置对应上
    private NacosDiscoveryProperties discoveryProperties;
    // nacos服务管理器对象
    private NacosServiceManager nacosServiceManager;

    public NacosServiceDiscovery(NacosDiscoveryProperties discoveryProperties,
                                 NacosServiceManager nacosServiceManager) {
        this.discoveryProperties = discoveryProperties;
        this.nacosServiceManager = nacosServiceManager;
    }

    /**
     * Return all instances for the given service.
     * <p>
     * 返回指定group和servic的所有实例
     *
     * @param serviceId id of service
     * @return list of instances
     * @throws NacosException nacosException
     */
    public List<ServiceInstance> getInstances(String serviceId) throws NacosException {
        // 配置文件中配置的group组名
        String group = discoveryProperties.getGroup();
        // namingService(): 通过反射创建一个NacosNamingService对象
        // 最终会调用NacosNamingService#selectInstances()方法
        // TODO 查看nacosClient端 的NacosNamingService#selectInstances(String serviceName, String groupName, boolean healthy, boolean subscribe)
        List<Instance> instances = namingService().selectInstances(serviceId, group,
                true);
        // 将Instance包装成NacosServiceInstance对象返回
        return hostToServiceInstanceList(instances, serviceId);
    }

    /**
     * Return the names of all services.
     * 返回指定group的所有服务名称
     *
     * 查看nacosClient端的com.alibaba.nacos.api.naming.NamingFactory#createNamingService(java.lang.String)
     * @return list of service names
     * @throws NacosException nacosException
     */
    public List<String> getServices() throws NacosException {
        // 配置文件中配置的group组名
        String group = discoveryProperties.getGroup();
        // namingService(): 通过反射创建一个NacosNamingService对象
        // 最终会调用NamingGrpcClientProxy#getServiceList()方法
        // TODO 进入getServicesOfServer
        // TODo 查看nacosClient端 的 NacosNamingService#getServicesOfServer
        ListView<String> services = namingService().getServicesOfServer(1,
                Integer.MAX_VALUE, group);
        // 返回所有服务名称
        return services.getData();
    }

    public static List<ServiceInstance> hostToServiceInstanceList(
            List<Instance> instances, String serviceId) {
        List<ServiceInstance> result = new ArrayList<>(instances.size());
        for (Instance instance : instances) {
            ServiceInstance serviceInstance = hostToServiceInstance(instance, serviceId);
            if (serviceInstance != null) {
                result.add(serviceInstance);
            }
        }
        return result;
    }

    public static ServiceInstance hostToServiceInstance(Instance instance,
                                                        String serviceId) {
        if (instance == null || !instance.isEnabled() || !instance.isHealthy()) {
            return null;
        }
        NacosServiceInstance nacosServiceInstance = new NacosServiceInstance();
        nacosServiceInstance.setHost(instance.getIp());
        nacosServiceInstance.setPort(instance.getPort());
        nacosServiceInstance.setServiceId(serviceId);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("nacos.instanceId", instance.getInstanceId());
        metadata.put("nacos.weight", instance.getWeight() + "");
        metadata.put("nacos.healthy", instance.isHealthy() + "");
        metadata.put("nacos.cluster", instance.getClusterName() + "");
        if (instance.getMetadata() != null) {
            metadata.putAll(instance.getMetadata());
        }
        metadata.put("nacos.ephemeral", String.valueOf(instance.isEphemeral()));
        nacosServiceInstance.setMetadata(metadata);

        if (metadata.containsKey("secure")) {
            boolean secure = Boolean.parseBoolean(metadata.get("secure"));
            nacosServiceInstance.setSecure(secure);
        }
        return nacosServiceInstance;
    }

    private NamingService namingService() {
        return nacosServiceManager.getNamingService();
    }

}
