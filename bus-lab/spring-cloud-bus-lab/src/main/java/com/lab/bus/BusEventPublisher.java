package com.lab.bus;

import com.lab.bus.event.CustomBusEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BusEventPublisher {

    @Autowired
    private ApplicationEventPublisher publisher;

    public BusEventPublisher() {
    }

    /**
     * 发布自定义总线事件
     * @param originService 事件源服务
     * @param destinationService 目标服务(*: 所有服务, serviceId: 指定服务)
     * @param message 事件消息
     * @param data 事件数据
     */
    public void publishCustomEvent(String originService, String destinationService, String message, String data) {
        CustomBusEvent event = new CustomBusEvent(this, originService, destinationService, message, data);
        System.out.println("发布自定义事件: " + event);
        publisher.publishEvent(event);

        // 添加延迟确保事件被处理
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void publishRefresh(String originService, String destinationService) {
//        RefreshRemoteApplicationEvent event = new RefreshRemoteApplicationEvent(this, originService, destinationService);
//        publisher.publishEvent(event);

        RefreshRemoteApplicationEvent event = new RefreshRemoteApplicationEvent(
                this, originService, destinationService);

        // 强制事件传播
        System.out.println("发布事件: " + event);
        publisher.publishEvent(event);

        // 添加延迟确保事件被处理
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}