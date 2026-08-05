package me.bottdev.meshdi.example;

import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.OnLifecycleEvent;

@Component
public class SomeService {

    public void doSomething() {
        System.out.println("Did something!");
    }

    @OnLifecycleEvent(BeanLifecycleEventType.PRE_DESTROY)
    public void cleanup() {
        System.out.println("Destroyed Some Service");
    }

}
