package util;


import java.util.*;
import java.util.stream.Collectors;

public class TreeDecomposition {
    public List<VertexBag> vertex_bags;
    public List<Set<Integer>> contained_in_subtree;
    public Integer root;
    public Integer width;

    public Map<Integer, List<Integer>> children;


    public TreeDecomposition(List<VertexBag> vertex_bags, Map<Integer, Integer> parent_mapping) {
        this.vertex_bags = vertex_bags;
        this.children = new HashMap<>();
        contained_in_subtree = new ArrayList<>();
        this.width = 0;
        for (int i = 0; i < vertex_bags.size(); ++i) {
            contained_in_subtree.add(new HashSet<>());
            this.width = Math.max(this.width, vertex_bags.get(i).size() - 1);
        }

        for (int i = 0; i < vertex_bags.size(); ++i) {
            children.put(i, new ArrayList<>());
            vertex_bags.get(i).set_type(VertexBag.BagType.LEAF);
            contained_in_subtree.get(i).addAll(vertex_bags.get(i).contained_vertices);
        }

        for (int i = 0; i < vertex_bags.size(); ++i) {
            if (!parent_mapping.containsKey(i)) {
                root = i;
            } else {
                int parent = parent_mapping.get(i);
                children.get(parent).add(i);
                VertexBag parent_bag = vertex_bags.get(parent);
                VertexBag this_bag = vertex_bags.get(i);
                switch (parent_bag.size() - this_bag.size()) {
                    case -1 -> parent_bag.set_type(VertexBag.BagType.FORGET);
                    case 0 -> parent_bag.set_type(VertexBag.BagType.JOIN);
                    case 1 -> parent_bag.set_type(VertexBag.BagType.INSERT);
                    default ->
                            System.out.printf("Parent bag contains %d vertices, this bag contains %d vertices", parent_bag.size(), this_bag.size());
                }
            }
        }
        for (int j = 0; j < vertex_bags.size(); ++j) {
            for (int i = 0; i < vertex_bags.size(); ++i) {
                if (!parent_mapping.containsKey(i)) {
                    continue;
                }
                int parent = parent_mapping.get(i);
                contained_in_subtree.get(parent).addAll(contained_in_subtree.get(i));

            }

        }
    }

    public void display(int node, String prefix) {
        System.out.printf("%s%d (%s) [%s]\n", prefix, node, vertex_bags.get(node).type, vertex_bags.get(node).contained_vertices.stream().map(String::valueOf).collect(Collectors.joining(",")));
        if (!children.containsKey(node))
            return;
        var cs = children.get(node);
        if (cs.size() == 1) {
            display(cs.get(0), prefix);
        } else {
            for (var c : cs) {
                display(c, prefix + " ");
            }
        }
    }

    public void reorder(Graph g) {
        Deque<Integer> deque = new ArrayDeque<>();
        deque.add(root);
        Map<Integer, Integer> chains = new HashMap<>();
        Set<Integer> chain_starts = new HashSet<>();
        Map<Integer, Integer> neighbor_count = new HashMap<>();
        while (!deque.isEmpty()) {
            var current = deque.pop();
            if (vertex_bags.get(current).type == VertexBag.BagType.INSERT){
                var child = children.get(current).get(0);
                chains.put(child, current);
                if (vertex_bags.get(child).type != VertexBag.BagType.INSERT) {
                    chain_starts.add(child);
                }
            }
            deque.addAll(children.get(current));
        }

        for (var start : chain_starts) {
            var base_set = vertex_bags.get(start).contained_vertices;
            var final_bag = chains.get(start);
            while (chains.containsKey(final_bag))
                final_bag = chains.get(final_bag);
            var final_set = vertex_bags.get(final_bag).contained_vertices;
            var added_vertices = new ArrayList<>(final_set.stream().filter(x -> !base_set.contains(x)).toList());
            added_vertices.sort(Comparator.comparingInt(x -> Utilities.intersection_size(g.adjacency_list.get(x), base_set)));
            var current = start;
            var last_bag = base_set;
            var last_containment = contained_in_subtree.get(start);
            for (int i = 0; i < added_vertices.size(); ++i) {
                var parent = chains.get(current);
                last_bag = new HashSet<>(last_bag);
                last_containment = new HashSet<>(last_containment);
                last_bag.add(added_vertices.get(i));
                last_containment.add(added_vertices.get(i));
                vertex_bags.get(parent).contained_vertices = last_bag;
                contained_in_subtree.set(parent, last_containment);
                current = parent;
            }
        }
    }

}
