package util;

import solver.Solver;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class VertexDomination {

    /*
     * From James Trimble's Paper: A vertex v dominates w, if N(w) \ {v} \subset N(v) \ {w} or N(w) \ {v} = N(v) \ {w} and v < w.
     * He says that there is always a way to construct a treedepth decomposition so that no vertex dominates one of its
     * ancestors.
     */

    public static boolean [][] dominates;

    public static Set<Integer> forbidden_descendants = new HashSet<>();
    public static Set<Integer> forbidden_ancestors = new HashSet<>();

    public static void update_domination(Graph g) {
        dominates = new boolean[g.vertices][g.vertices];
        var neighbors = new ArrayList<HashSet<Integer>>();

        for (int i = 0; i < g.vertices; ++i) {
            neighbors.add(new HashSet<>(g.adjacency_list.get(i)));
            for (int j = 0; j < i; ++j) {
                var set_i = neighbors.get(i);
                var set_j = neighbors.get(j);
                var present_j = set_i.remove(j);
                var present_i = set_j.remove(i);
                if (set_i.containsAll(set_j)) {
                    dominates[i][j] = true;
                }
                else if (set_j.containsAll(set_i) && set_i.size() < set_j.size()) {
                    dominates[j][i] = true;
                }
                if (present_j) {
                    set_i.add(j);
                }
                if (present_i) {
                    set_j.add(i);
                }
            }
        }
    }

    public static void query_vertex(int vertex, Set<Integer> relevant_vertices) {
        forbidden_ancestors = new HashSet<>();
        forbidden_descendants = new HashSet<>();
        if (Solver.VERTEX_DOMINATION) {
            for (var other_vertex : relevant_vertices) {
                if (VertexDomination.dominates[vertex][other_vertex]) {
                    forbidden_ancestors.add(other_vertex);
                } else if (VertexDomination.dominates[other_vertex][vertex]) {
                    forbidden_descendants.add(other_vertex);
                }
            }
        }
//        System.out.printf("%d dominates [%s] and is dominated by [%s]\n", vertex,
//                forbidden_ancestors.stream().map(String::valueOf).collect(Collectors.joining(",")),
//                forbidden_descendants.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

}
