package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.ModuleCandidate;

interface InternalModuleCandidate extends ModuleCandidate {

    InternalModuleHandle createHandle();

}
