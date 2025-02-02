package util;

import java.util.*;
import java.util.stream.Collectors;

/*
 * This class makes sure that every structure of deleted vertices is only generated once, so if the same structure
 * appears in multiple partial decompositions (which will happen eventually) we save a lot of space
 */
public class RemovedTreeHandler {
    private static final Map<RemovedTreeMapWrapper, RemovedTree> super_trees = new HashMap<>();

    public static RemovedTree request_tree(Integer vertex, List<RemovedTree> removed_trees) {
        var wrapper = new RemovedTreeMapWrapper(vertex, removed_trees);
        if (super_trees.containsKey(wrapper))
            return super_trees.get(wrapper);

        RemovedTree super_tree = new RemovedTree(vertex, removed_trees);
        super_trees.put(wrapper, super_tree);
        return super_tree;
    }

    public static void show_information() {
        System.out.printf("Total removed trees saved: %d\n", super_trees.keySet().size());
    }

    private static class RemovedTreeMapWrapper {
        final int root;
        final Set<Integer> hashcodes;
        public RemovedTreeMapWrapper(int root, List<RemovedTree> children) {
            this.root = root;
            // default hashcode implementation guarantees unique hashcodes for all RemovedTrees.
            this.hashcodes = children.stream().map(Object::hashCode).collect(Collectors.toSet());
        }

        @Override
        public int hashCode() {
            return root + hashcodes.stream().reduce(0, Integer::sum);
        }

        @Override
        public boolean equals(Object obj) {
            RemovedTreeMapWrapper other = (RemovedTreeMapWrapper) obj;
            if (root != other.root)
                return false;
            return hashcodes.equals(other.hashcodes);
        }
    }
}
