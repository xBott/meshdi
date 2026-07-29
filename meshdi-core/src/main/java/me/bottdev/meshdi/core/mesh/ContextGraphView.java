package me.bottdev.meshdi.core.mesh;

import java.util.List;
import java.util.Set;

public interface ContextGraphView {

    void addNode(String id);

    void removeNode(String id);

    void addEdge(String fromId, String toId);

    Set<String> successors(String id);

    Set<String> predecessors(String id);

    List<String> bfs(String id);

    void clear();

}
