package me.bottdev.meshdi.example;

import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.core.SimpleContextBootstrap;

import static me.bottdev.kern.commons.key.KeyUtils.key;

public class Main {

    public static void main(String[] args) throws ContextBuildException, ContextStartException {

        Context context = SimpleContextBootstrap
                .bootstrap(Main.class.getClassLoader())
                .id("main")
                .build();
        context.start();

        AnotherService anotherService = context.getResolver().get(key(AnotherService.class));
        System.out.println(anotherService);

        context.dispose();

    }

}
