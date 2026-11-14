package me.bottdev.meshdi.api.exceptions.mesh;

import lombok.Getter;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.exceptions.DiRuntimeException;

import java.util.List;

/// Thrown when a key is bound by more than one context visible from the requesting context,
/// so the mesh cannot pick an owner without guessing.
@Getter
public class MeshAmbiguousLookupException extends DiRuntimeException {

    private final String fromId;
    private final TypedKey<?> key;
    private final List<String> owners;

    public MeshAmbiguousLookupException(String fromId, TypedKey<?> key, List<String> owners) {
        super("Key " + key + " requested by context \"" + fromId + "\" is bound by several visible contexts: "
                + String.join(", ", owners) + ". Add a qualifier or narrow the visibility.");
        this.fromId = fromId;
        this.key = key;
        this.owners = List.copyOf(owners);
    }

}
