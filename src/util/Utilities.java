package util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Utilities {
    public static <T> List<List<T>> generate_all_subsets(Collection<T> superset) {
        List<List<T>> subsets = new ArrayList<>();

        // We can interpret each subset as a number from 0 to 2^n-1
        // where a 1 in binary representation at digit i means to include the i-th item of the superset
        for (int subset = 0; subset < (1 << superset.size()); ++subset) {
            List<T> current_subset = new ArrayList<>();
            int index = 0;
            for (var item : superset) {
                // check whether this item should be included
                if ((subset & (1 << index)) > 0)
                    current_subset.add(item);
                index += 1;
            }
            subsets.add(current_subset);
        }
        return subsets;
    }

    /*
     * This function generates all possible merges of two lists of the specified size (the order of the elements has
     * to stay the same). It generates each merge as a list of integers, where the integers say how many items of the
     * first list are between two consecutive items of the second list. (This also means that the resulting lists
     * all have size second_size + 1
     */
    public static List<List<Integer>> generate_all_merges(int first_size, int second_size) {
        List<List<Integer>> merges = new ArrayList<>();
        List<Integer> first_merge = IntStream.range(0, second_size + 1)
                .boxed()
                .map(x -> x == 0 ? first_size : 0)
                .collect(Collectors.toList());
        merges.add(first_merge);

        while (true){
            var last_merge = merges.get(merges.size() - 1);
            // the last merge that can be generated will be of this form
            int elements_in_last_bucket = last_merge.get(second_size);
            if (elements_in_last_bucket == first_size)
                return merges;
            var next_merge = new ArrayList<>(last_merge);
            for (int i = second_size - 1; i >= 0; --i) {
                if (next_merge.get(i) > 0) {
                    next_merge.set(i, next_merge.get(i) - 1);
                    if (i + 1 != second_size) {
                        next_merge.set(second_size, 0);
                    }
                    next_merge.set(i + 1, elements_in_last_bucket + 1);
                    break;
                }
            }
            merges.add(next_merge);
        }
    }


    public static Pair<Boolean, Map<Integer, Integer>> share_advanced_structure(RootedTree first, RootedTree second, Set<Integer> active_vertices) {
        var first_structure = first.get_advanced_structure();
        var second_structure = second.get_advanced_structure();
        var mapping = new HashMap<Integer, Integer>();
        var seen_vertices = new HashSet<Integer>();
        var pairs = new ArrayDeque<Pair<Integer, Integer>>();
        for (var vertex : active_vertices) {
            pairs.add(new Pair<>(vertex, vertex));
        }
        while (!pairs.isEmpty()) {
            var current_pair = pairs.pop();
            var vertex = current_pair.first;
            var other  = current_pair.second;
            if (active_vertices.contains(vertex) ^ active_vertices.contains(other))
                return new Pair<>(false, null);
            if (mapping.containsKey(vertex) ^ seen_vertices.contains(other))
                return new Pair<>(false, null);
            if (mapping.containsKey(vertex) && !other.equals(mapping.get(vertex)))
                return new Pair<>(false, null);
            if (vertex == -1 ^ other == -1)
                return new Pair<>(false, null);
            seen_vertices.add(other);
            mapping.put(vertex, other);
            if (vertex != -1) {
                pairs.add(new Pair<>(first_structure.get(vertex), second_structure.get(other)));
            }
        }
        return new Pair<>(true, mapping);
    }

    public static boolean share_simple_structure_on_advanced_map(RootedTree first, RootedTree second, Set<Integer> active_vertices) {
        var first_structure = first.get_advanced_structure();
        var second_structure = second.get_advanced_structure();
        for (int active_vertex : active_vertices) {
            var p1 = first.parent_mapping.get(active_vertex);
            while (!first.is_active(p1)) {
                p1 = first.parent_mapping.get(p1);
            }
            var p2 = second.parent_mapping.get(active_vertex);
            while (!second.is_active(p2)) {
                p2 = second.parent_mapping.get(p2);
            }
            if (!p1.equals(p2))
                return false;
        }
        return true;
    }

    // this won't work correctly if the provided mapping is not injective.
    public static Map<Integer, Integer> invert_mapping(Map<Integer, Integer> input_mapping) {
        HashMap<Integer, Integer> inverted_map = new HashMap<Integer, Integer>();
        for (var key : input_mapping.keySet()) {
            inverted_map.put(input_mapping.get(key), key);
        }
        return inverted_map;
    }

    // Builds the vertex mapping which is used to check for Dominance. If no vertex mapping can be constructed, returns null
    public static Map<Integer, Integer> build_vertex_mapping(RootedTree from, RootedTree to, Map<Integer, Integer> structure_mapping) {
        var reverse_mapping = invert_mapping(structure_mapping);
        var structural_vertices = new HashSet<>(structure_mapping.keySet());
        for (var from_vertex : structural_vertices) {
            if (from_vertex == -1)
                continue;
            var to_vertex = structure_mapping.get(from_vertex);
            // in this case the structural vertex itself already violates the necessary condition.
            if (from.get_depth_map().get(from_vertex) < to.get_depth_map().get(to_vertex))
                return null;
            // start with a vertex right above a structural vertex
            from_vertex = from.parent_mapping.get(from_vertex);
            to_vertex = to.parent_mapping.get(to_vertex);
            if (from_vertex == -1)
                continue;
            // map all non structure vertices (go up until we reach a structural vertex)
            while (!structure_mapping.containsKey(from_vertex)) {
                // find a vertex in the other tree that can be mapped to
                // i.e. that has less or the same depth, is a structurally sensible choice, and has less or the same height
                while (from.get_depth_map().get(from_vertex) < to.get_depth_map().get(to_vertex)) {
                    // we must stay in this structural "strip"
                    if (reverse_mapping.containsKey(to_vertex))
                        return null;
                    to_vertex = to.parent_mapping.get(to_vertex);
                }
                structure_mapping.put(from_vertex, to_vertex);
                from_vertex = from.parent_mapping.get(from_vertex);
            }
        }
        return structure_mapping;
    }

    public static Map<Integer, Integer> build_subtree_mapping(RootedTree from, RootedTree to, Map<Integer, Integer> structure_mapping, Map<Integer, Integer> vertex_mapping) {
        var subtree_mapping = new HashMap<Integer, Integer>();
        for (int from_vertex : from.leaves) {
            int from_parent = from.parent_mapping.get(from_vertex);
            int to_vertex = structure_mapping.get(from_vertex);
            int to_parent = vertex_mapping.get(from_parent);
            while (from_vertex != -1 && !subtree_mapping.containsKey(from_vertex)) {
                while (to.parent_mapping.get(to_vertex) != to_parent) {
                    to_vertex = to.parent_mapping.get(to_vertex);
                }
                if (from.get_depth_map().get(from_vertex) + from.height_mapping.get(from_vertex) < to.get_depth_map().get(to_vertex) + to.height_mapping.get(to_vertex)) {
                    return null;
                }
                subtree_mapping.put(from_vertex, to_vertex);
                from_vertex = from.parent_mapping.get(from_vertex);
            }
        }
        return subtree_mapping;
    }

    // Tests whether two trees share the same structure (based on a set of active vertices
    // This might warrant another look, are inactive vertex labels really ignored?
    public static boolean share_simple_structure(RootedTree first, RootedTree second, Set<Integer> active_vertices) {
        var first_structure = first.get_simple_structure();
        var second_structure = second.get_simple_structure();
        for (int active_vertex : active_vertices) {
            // if the structure does not match at any point
            if (!first_structure.get(active_vertex).equals(second_structure.get(active_vertex))) {
//                System.out.printf("%d -> %d in this tree, but %d -> %d in the other tree\n", active_vertex, structure.get(active_vertex), active_vertex, other_structure.get(active_vertex));
                return false;
            }
        }
        return true;
    }

    public static <S, T> void print_map(Map<Integer, Integer> map) {
        for (var key : map.keySet()) {
            System.out.println(key + " -> " + map.get(key));
        }
    }

    public static class Pair<F, S> {
        public F first;
        public S second;
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
    public static <T> int not_contained(Collection<T> candidates, Set<T> compare_against) {
        return candidates.size() - intersection_size(candidates, compare_against);
    }

    public static <T> T get_difference(Set<T> first, Set<T> second) {
        Set<T> result = new HashSet<>();
        for (var elem : first) {
            if (!second.contains(elem)) {
                return elem;
            }
        }
        return null;
    }

    public static <T> int intersection_size(Collection<T> candidates, Set<T> compare_against) {
        int counter = 0;
        for (var c : candidates) {
            if (compare_against.contains(c))
                counter++;
        }
        return counter;
    }

    public static <T> T fast_delete(List<T> list, int index) {
        var item = list.get(index);
        list.set(index, list.get(list.size() - 1));
        list.remove(list.size() - 1);
        return item;
    }
}
