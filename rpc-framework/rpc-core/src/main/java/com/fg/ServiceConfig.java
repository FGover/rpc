package com.fg;

public class ServiceConfig<T> {

    private Class<T> interfaceService;
    private Object ref;

    public Class<T> getInterface() {
        return interfaceService;
    }

    public void setInterface(Class<T> interfaceService) {
        this.interfaceService = interfaceService;
    }

    public Object getRef() {
        return ref;
    }

    public void setRef(Object ref) {
        this.ref = ref;
    }
}
