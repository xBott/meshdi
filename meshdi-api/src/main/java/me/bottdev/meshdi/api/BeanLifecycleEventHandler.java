package me.bottdev.meshdi.api;

@FunctionalInterface
public interface BeanLifecycleEventHandler<T> {

    void handle(T bean) throws Exception;

}
