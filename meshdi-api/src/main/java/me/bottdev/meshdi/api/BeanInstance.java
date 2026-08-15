package me.bottdev.meshdi.api;

public record BeanInstance<T>(Binding<T> binding, T value) {}
