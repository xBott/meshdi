package me.bottdev.meshdi.example;

import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.meshdi.api.annotations.Component;
import me.bottdev.meshdi.api.annotations.Dependency;

@Component(qualifier = "ANOTHER")
public class AnotherService {

    private final SomeService someService;

    public AnotherService(
            @Dependency(link = DependencyLink.OPTIONAL) SomeService someService
    ) {
        this.someService = someService;
    }

}
