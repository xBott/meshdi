package me.bottdev.example.root;

import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.moduleit.api.annotations.Module;

@Component
@Module(
        id = "root",
        version = "0.0.1",
        apiVersion = ">=0.0.1",
        exports = { "me.bottdev.example.root" }
)
public class RootModule {

    public void doSomething() {
        System.out.println("ROOT MODULE IS WORKING!");
    }

}
