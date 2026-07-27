package me.bottdev.meshdi.core.mesh;

import me.bottdev.kern.struct.algorithms.traverse.TraversalStep;
import me.bottdev.kern.struct.algorithms.traverse.Traversals;
import me.bottdev.kern.struct.graph.EndpointPairs;
import me.bottdev.kern.struct.graph.MutableGraph;
import me.bottdev.kern.struct.graph.adjacency.AdjacencyListGraphBuilder;
import me.bottdev.kern.struct.graph.endpoints.Directed;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleContextGraphView implements ContextGraphView {

    private final MutableGraph<String, Directed<String>> graph =
            new AdjacencyListGraphBuilder<String, Directed<String>>().mutable();

    @Override
    public void addNode(String id) {
        graph.addNode(id);
    }

    @Override
    public void removeNode(String id) {

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
                .toList();
    }


}
