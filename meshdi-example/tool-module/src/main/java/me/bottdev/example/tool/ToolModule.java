package me.bottdev.example.tool;

import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.OnLifecycleEvent;
import me.bottdev.example.root.RootModule;
import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
import me.bottdev.meshdi.moduleit.api.annotations.Module;

@Component(init = InitializationStrategy.EAGER)
@Module(
        id = "tool",
        version = "0.0.1",
        apiVersion = ">=0.0.1",
        dependencies = {
                @DependsOn(id = "root")
        }
)
public class ToolModule {

    private final RootModule rootModule;

    public ToolModule(
            @Dependency(link = DependencyLink.OPTIONAL) RootModule rootModule
    ) {
        this.rootModule = rootModule;
    }

    @OnLifecycleEvent(BeanLifecycleEventType.POST_CONSTRUCT)
    public void start() {
        System.out.println("Starting tool module");
        rootModule.doSomething();
    }

}
