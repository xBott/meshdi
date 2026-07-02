package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.ContextBuildException;

public interface ContextBuilder<T extends Context> {

    T build() throws ContextBuildException;

}
