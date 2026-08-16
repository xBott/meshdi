package me.bottdev.meshdi.core.mesh.views;

import me.bottdev.kern.struct.algorithms.traverse.TraversalStep;
import me.bottdev.kern.struct.algorithms.traverse.Traversals;
import me.bottdev.kern.struct.graph.EndpointPairs;
import me.bottdev.kern.struct.graph.MutableGraph;
import me.bottdev.kern.struct.graph.adjacency.AdjacencyListGraphBuilder;
import me.bottdev.kern.struct.graph.endpoints.Directed;
import me.bottdev.meshdi.core.mesh.ContextGraphView;

import java.util.List;
import java.util.Set;

public class KernContextGraphView implements ContextGraphView {

    private final MutableGraph<String, Directed<String>> graph =
            new AdjacencyListGraphBuilder<String, Directed<String>>().mutable();

    @Override
    public void addNode(String id) {
        graph.addNode(id);
    }

    @Override
    public void removeNode(String id) {
        graph.removeNode(id);
    }

    @Override
    public void addEdge(String fromId, String toId) {
        graph.addEdge(EndpointPairs.directed(fromId, toId));
    }

    @Override
    public Set<String> successors(String id) {
        return graph.successors(id);
    }

    @Override
    public Set<String> predecessors(String id) {
        return graph.predecessors(id);
    }

    @Override
    public List<String> bfs(String id) {
        return Traversals
                .bfs()
                .on(graph)
                .from(id)
                .allowDuplicates(false)
                .stream()
                .map(TraversalStep::node)
                .filter(contextId -> !contextId.equals(id))
                .toList();
    }

    @Override
    public void clear() {
        graph.clear();
    }

}
