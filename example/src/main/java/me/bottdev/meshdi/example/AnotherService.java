package me.bottdev.meshdi.example;

import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.OnLifecycleEvent;

@Component
public class AnotherService {

    private final SomeService someService;

    public AnotherService(
            SomeService someService
    ) {
        this.someService = someService;
    }

    @OnLifecycleEvent(BeanLifecycleEventType.POST_CONSTRUCT)
    public void start() {
        System.out.println("Started Another Service");
        someService.doSomething();
    }

    @OnLifecycleEvent(BeanLifecycleEventType.PRE_DESTROY)
    public void cleanup() {
        System.out.println("Destroyed Another Service");
    }

}
