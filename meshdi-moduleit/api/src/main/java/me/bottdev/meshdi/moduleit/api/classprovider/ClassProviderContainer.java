package me.bottdev.meshdi.moduleit.api.classprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ClassProviderContainer(List<ClassProvider> providers) {

    public ClassProviderContainer {
        List<ClassProvider> sorted = new ArrayList<>(providers);
        Collections.sort(sorted);
        providers = Collections.unmodifiableList(sorted);
    }

}
