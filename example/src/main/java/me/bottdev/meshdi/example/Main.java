package me.bottdev.meshdi.example;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;

//import static me.bottdev.kern.commons.key.KeyUtils.key;

public class Main {

    public static void main(String[] args) throws ContextBuildException, ContextStartException {

        Context context = SimpleContextBootstrap
                .bootstrap(Main.class.getClassLoader())
                .id("main")
                .build();
        context.start();

//        SomeService someService = context.getResolver().get(key(SomeService.class));
//        System.out.println(someService);

    }

}
