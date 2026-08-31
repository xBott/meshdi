package me.bottdev.example.root;

import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.OnLifecycleEvent;
import me.bottdev.meshdi.moduleit.api.annotations.Module;

@Component
@Module(
        id = "root",
        version = "0.0.1",
        apiVersion = "*",
        exports = { "me.bottdev.example.root" }
)
public class RootModule {

    @OnLifecycleEvent(BeanLifecycleEventType.POST_CONSTRUCT)
    public void onStart() {
        System.out.println("ROOT MODULE IS STARTED SUCCESSFULLY!");
    }

    @OnLifecycleEvent(BeanLifecycleEventType.PRE_DESTROY)
    public void onStop() {
        System.out.println("ROOT MODULE IS STOPPED SUCCESSFULLY!");
    }

    public void doSomething() {
        System.out.println("ROOT MODULE IS WORKING AND USED EXTERNALLY!");
    }

}
