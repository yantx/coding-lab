package com.lab.eureka.loadbalancer;

public class ServiceInstanceHolder {
    private static final ThreadLocal<String> INSTANCE = new ThreadLocal<String>();

    public static void set(String value) {
        INSTANCE.set(value);
    }

    public static String get() {
        return INSTANCE.get();
    }

    public static void remove() {
        INSTANCE.remove();
    }
}
