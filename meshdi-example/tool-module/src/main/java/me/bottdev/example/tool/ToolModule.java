package me.bottdev.example.tool;

import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;
import me.bottdev.meshdi.api.annotations.OnLifecycleEvent;
import me.bottdev.example.root.RootModule;
import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
import me.bottdev.meshdi.moduleit.api.annotations.Library;
import me.bottdev.meshdi.moduleit.api.annotations.Module;
import me.bottdev.meshdi.moduleit.api.annotations.Repository;
import okhttp3.*;

import java.io.IOException;

@Component(init = InitializationStrategy.EAGER)
@Module(
        id = "tool",
        version = "0.0.1",
        apiVersion = ">=1.0.0",
        dependencies = {
                @DependsOn(id = "root")
        },
        repositories = {
                @Repository(id = "nimbra-reposilite", url = "https://reposlite.nimbra.net/snapshots")
        },
        libraries = {
                @Library("com.squareup.okhttp3:okhttp:5.0.0-alpha.12"),
                @Library("me.bottdev:kern-struct:0.0.44-SNAPSHOT")
        }
)
public class ToolModule {

    private final RootModule rootModule;
    private final OkHttpClient client;

    public ToolModule(
            @Dependency(link = DependencyLink.OPTIONAL) RootModule rootModule
    ) {
        this.rootModule = rootModule;
        this.client = new OkHttpClient();
    }

    @OnLifecycleEvent(BeanLifecycleEventType.POST_CONSTRUCT)
    public void onStart() {
        System.out.println("TOOL MODULE IS STARTED SUCCESSFULLY!");
        rootModule.doSomething();
        request();
    }

    private void request() {
        String url = "https://publicobject.com/helloworld.txt";
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request to the server was not successfull: " +
                        response.code() + " " + response.message());
            }

            System.out.println("Server: " + response.header("Server"));
            System.out.println(response.body().string());

        } catch (IOException ex) {
            System.out.println("Failed to request: " + url);

        }

    }

    @OnLifecycleEvent(BeanLifecycleEventType.PRE_DESTROY)
    public void onStop() {
        System.out.println("TOOL MODULE IS STOPPED SUCCESSFULLY!");
    }

}
