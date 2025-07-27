package com.lab.bus.event;

import org.springframework.cloud.bus.event.RemoteApplicationEvent;

import java.io.Serializable;

public class CustomBusEvent extends RemoteApplicationEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String message;
    private String data;

    // 必须有无参构造函数，用于反序列化
    public CustomBusEvent() {
        super();
    }

    public CustomBusEvent(Object source, String originService, String destinationService, String message, String data) {
        super(source, originService, destinationService);
        this.message = message;
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return "CustomBusEvent{" +
                "message='" + message + '\'' +
                ", data='" + data + '\'' +
                ", originService='" + getOriginService() + '\'' +
                ", destinationService='" + getDestinationService() + '\'' +
                '}';
    }
}
