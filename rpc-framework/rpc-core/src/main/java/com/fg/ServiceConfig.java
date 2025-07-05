package com.fg;

import lombok.Getter;
import lombok.Setter;

public class ServiceConfig<T> {

    private Class<?> interfaceService;
    @Getter
    @Setter
    private Object ref;
    @Getter
    @Setter
    private String group = "default";

    public Class<?> getInterface() {
        return interfaceService;
    }

    public void setInterface(Class<?> interfaceService) {
        this.interfaceService = interfaceService;
    }

}
