package com.lab.bus;

import com.lab.bus.event.CustomBusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.bus.event.AckRemoteApplicationEvent;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.event.SentApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BusEventListener {

    @EventListener
    public void handleAllRemoteEvents(RemoteApplicationEvent event) {
        log.info("总线事件诊断 >> 类型: {} 来源: {} 目标: {}",
                event.getClass().getSimpleName(),
                event.getOriginService(),
                event.getDestinationService());
    }

    @EventListener
    public void handleAckEvent(AckRemoteApplicationEvent event) {
        log.info("收到 ACK 事件: 来源={}, 目标={}, 类型={}",
                event.getOriginService(),
                event.getDestinationService(),
                event.getEvent().getTypeName());
    }
    
    /**
     * 处理自定义总线事件
     */
    @EventListener
    public void handleCustomEvent(CustomBusEvent event) {
        log.info("收到自定义事件: 消息='{}', 数据='{}', 来源={}, 目标={}",
                event.getMessage(),
                event.getData(),
                event.getOriginService(),
                event.getDestinationService());
    }

    @EventListener
    public void handleSentEvent(SentApplicationEvent event) {
        log.info("收到 SENT 事件: 来源={}, 目标={}, 类型={}",
                event.getOriginService(),
                event.getDestinationService(),
                event.getType());
    }

    @EventListener
    public void handleRefreshEvent(RefreshRemoteApplicationEvent event) {
        log.info("收到 REFRESH 事件: 来源={}, 目标={}",
                event.getOriginService(),
                event.getDestinationService());
    }
}