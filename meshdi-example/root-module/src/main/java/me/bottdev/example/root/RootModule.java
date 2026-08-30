package me.bottdev.example.root;

import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.OnLifecycleEvent;
import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
import me.bottdev.meshdi.moduleit.api.annotations.Module;

@Component
@Module(
        id = "root",
        version = "0.0.1",
        apiVersion = ">=1.0.0",
        exports = { "me.bottdev.example.root" },
        dependencies = {
                @DependsOn(id = "api")
        }
)
public class RootModule {

    private final String name;

    public RootModule(
            @Dependency(qualifier = "name", link = DependencyLink.OPTIONAL) String name
    ) {
        System.out.println(name);
        this.name = name;
    }

    @OnLifecycleEvent(BeanLifecycleEventType.POST_CONSTRUCT)
    public void onStart() {
        System.out.println("ROOT MODULE IS STARTED SUCCESSFULLY!");
        System.out.println("USING API: " + name);
    }

    @OnLifecycleEvent(BeanLifecycleEventType.PRE_DESTROY)
    public void onStop() {
        System.out.println("ROOT MODULE IS STOPPED SUCCESSFULLY!");
    }

    public void doSomething() {
        System.out.println("ROOT MODULE IS WORKING AND USED EXTERNALLY!");
    }

}
