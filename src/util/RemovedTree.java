package util;

import java.util.*;

public class RemovedTree {
    public int root;
    public int height;
    List<RemovedTree> subtrees;

    public RemovedTree(int vertex) {
        root = vertex;
        height = 1;
    }

    public RemovedTree(int vertex, List<RemovedTree> subtrees) {
        root = vertex;
        height = subtrees.stream().map((t) -> t.height).reduce(0, Math::max) + 1;
        this.subtrees = subtrees;
    }

    /*
     * This method should never be called during the algorithm as it explicitly generates the tree structure
     * that is hidden in this pointer-structure
     */
    public Map<Integer, Set<Integer>> generate_structure() {
        Map<Integer, Set<Integer>> children = new HashMap<>();
        children.put(root, new HashSet<>());
        for (var subtree : subtrees) {
            children.get(root).add(subtree.root);
            children.putAll(subtree.generate_structure());
        }
        return children;
    }

    public Map<Integer, Integer> generate_parent_mapping() {
        Map<Integer, Integer> parent_mapping = new HashMap<>();
        for (var subtree : subtrees) {
            parent_mapping.put(subtree.root, root);
            parent_mapping.putAll(subtree.generate_parent_mapping());
        }
        return parent_mapping;
    }

    public boolean contains(int vertex) {
        if (root == vertex) {
            return true;
        }
        for (var child : subtrees) {
            if (child.contains(vertex))
                return true;
        }
        return false;
    }
}
