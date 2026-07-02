package me.bottdev.meshdi.api;

public record ContextMeshLookup<T>(
        Context owner,
        Binding<T> binding
) {}
