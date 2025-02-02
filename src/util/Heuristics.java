package util;

import java.util.*;

public class Heuristics {
    public static int lower_bound(RootedTree partial_solution, Set<Integer> contained_in_subtree, Graph g) {
        return 0;
//        var double_neighbors = new ArrayList<Integer>();
//        var find_path = new HashSet<Integer>();
//        var neighbor_mapping = new HashMap<Integer, ArrayList<Integer>>();
//        var added_vertices = new HashMap<Integer, Integer>();
//
//        for (int vertex = 0; vertex < g.vertices; ++vertex) {
//            if (contained_in_subtree.contains(vertex))
//                continue;
//            var active_neighbors = new ArrayList<Integer>();
//            for (var neighbor : g.adjacency_list.get(vertex)) {
//                if (partial_solution.active_vertices.contains(neighbor)) {
//                    active_neighbors.add(neighbor);
//                }
//            }
//            neighbor_mapping.put(vertex, active_neighbors);
//            if (active_neighbors.size() >= 2) {
//                double_neighbors.add(vertex);
//            } else {
//                find_path.add(vertex);
//            }
//        }
//        var copied_mapping = new HashMap<>(partial_solution.height_mapping);
//
//        // handle neighbors that are connected to multiple vertices in the tree; the lowest (and best) point they could
//        // possibly be inserted in an optimal solution is at the height of the inactive vertices;
//        for (var vertex : double_neighbors) {
//            var active_neighbors = neighbor_mapping.get(vertex);
//            var result = partial_solution.get_insert_point(active_neighbors);
//            if (result.first)
//                add_vertex(added_vertices, result.second);
//        }
//        return calculate_height(partial_solution, added_vertices);
    }

    private static void add_vertex(Map<Integer, Integer> added_vertices, int vertex) {
        if (!added_vertices.containsKey(vertex)) {
            added_vertices.put(vertex, 1);
            return;
        }
        added_vertices.put(vertex, added_vertices.get(vertex) + 1);
    }

    private static int calculate_height(RootedTree original, Map<Integer, Integer> added_vertices) {
        var seen_vertices = new HashSet<Integer>();
        var heights = new HashMap<Integer, Integer>();
        var to_handle = new ArrayDeque<>(original.leaves);
        while (!to_handle.isEmpty()) {
            var current = to_handle.pollFirst();
            if (current != -1) {
                var parent = original.parent_mapping.get(current);
                if (!seen_vertices.contains(parent)) {
                    to_handle.add(parent);
                    seen_vertices.add(parent);
                }
            }
            var alive_height = original.alive_children.get(current).stream().map(original.height_mapping::get).reduce(Math::max).orElse(0);
            var dead_height = original.removed_subtrees.get(current).stream().map(x -> x.height).reduce(Math::max).orElse(0);
            heights.put(current, Math.max(alive_height, dead_height) + 1 + added_vertices.getOrDefault(current, 0));
        }
        return heights.get(-1) - 1;
    }

    private static void increase_height(Map<Integer, Integer> height_mapping, Map<Integer, Integer> parent_mapping, int vertex) {
        var height = height_mapping.get(vertex);
        height_mapping.put(vertex, height + 1);
        var parent = parent_mapping.get(vertex);
        while (vertex != -1 && height + 1 >= height_mapping.get(parent_mapping.get(vertex))) {
            height_mapping.put(parent, height + 2);
            height += 1;
            vertex = parent_mapping.get(vertex);
        }
    }

}
