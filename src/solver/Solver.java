package solver;

import io.GraphReader;
import io.GraphWriter;
import testing.GraphPainter;
import util.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Solver {
    public static boolean DEBUG_MODE = false;
    public static boolean DO_SWAPS = true;
    public static boolean VERTEX_DOMINATION = true;

    public static Set<Integer> current_active_vertices = new HashSet<>();

    final String SOLUTION_FOLDER = "partials";
    Graph graph;
    TreeDecomposition ntd;
    public RootedTreeSet results;

    public RootedTree solution = null;
    int max_depth;

    List<Utilities.Pair<String, RootedTree>> equivalent_solutions;

    GraphPainter gp;



    public Solver(Graph graph, TreeDecomposition ntd, int max_depth) throws IOException {
        this.graph = graph;
        this.ntd = ntd;
        this.max_depth = max_depth;
    }

    private RootedTreeSet solve(int bag_id) throws IOException{
        var ntdbag = ntd.vertex_bags.get(bag_id);
        if (ntdbag.type == VertexBag.BagType.LEAF) {
            var tree = new RootedTree(ntdbag.contained_vertices.stream().findFirst().get());
            tree.history.bag_id = bag_id;
            return new RootedTreeSet(tree);
        }
        var child_id = ntd.children.get(bag_id).get(0);
        var child_trees = solve(child_id);

        var solutions = new RootedTreeSet();
        switch (ntdbag.type) {
            case FORGET -> {
                current_active_vertices = ntdbag.contained_vertices;
                Integer old_vertex = null;
//                System.out.printf("Restricting to [%s]\n", ntdbag.contained_vertices.stream().map(String::valueOf).collect(Collectors.joining(", ")));
                for (var current_tree : child_trees) {
                    if (old_vertex == null) {
                        old_vertex = Utilities.get_difference(current_tree.active_vertices, current_active_vertices);
                    }
                    var restricted = current_tree.forget_other(ntdbag.contained_vertices);
                    update_partial_decompositions(restricted, solutions);
                }
                System.out.printf("Finished forget node %d (forgot %d), %d partial decompositions.\n", bag_id, old_vertex, solutions.size());
            }
            case INSERT -> {
                var child_bag = ntd.vertex_bags.get(child_id);
                int new_vertex = ntdbag.contained_vertices.stream()
                        .filter(x -> !child_bag.contained_vertices.contains(x))
                        .findFirst()
                        .get();
                List<Integer> neighbors = graph.adjacency_list.get(new_vertex).stream()
                        .filter(ntdbag.contained_vertices::contains)
                        .collect(Collectors.toList());
                current_active_vertices = ntdbag.contained_vertices;
//                System.out.printf("Introducing %d as neighbor of [%s]\n", new_vertex, neighbors.stream().map(String::valueOf).collect(Collectors.joining(", ")));
                VertexDomination.query_vertex(new_vertex, ntd.contained_in_subtree.get(bag_id));
                for (var current_tree : child_trees) {
                    var post_insertion = current_tree.introduce(new_vertex, neighbors, max_depth);
                    for (var this_inserted : post_insertion) {
                        if (Heuristics.lower_bound(this_inserted, ntd.contained_in_subtree.get(bag_id), graph) <= max_depth)
                            update_partial_decompositions(this_inserted, solutions);
                    }
                }
                System.out.printf("Finished introduce node %d (introduced %d), %d partial decompositions.\n", bag_id, new_vertex, solutions.size());
            }
            default -> {
                int child2 = ntd.children.get(bag_id).get(1);
                var child2_trees = solve(child2);
                current_active_vertices = ntdbag.contained_vertices;
//                System.out.printf("Joining %d with %d trees at bag with id %d\n", child_trees.size(), child2_trees.size(), bag_id);
                for (var tree1 : child_trees) {
                    for (var tree2 : child2_trees.get_compatible_trees(tree1)) {
                        var joined = tree1.join_v3(tree2, max_depth);
                        for (var this_join : joined) {
                            if (Heuristics.lower_bound(this_join, ntd.contained_in_subtree.get(bag_id), graph) <= max_depth) {
                                update_partial_decompositions(this_join, solutions);
                            }
                        }
                    }
                }
                System.out.println("Finished join node " + (bag_id) + ", " + solutions.size() + " partial decompositions.");
            }
        }
        solutions.show_information();
        // RemovedTreeHandler.show_information();
        if (DEBUG_MODE && bag_id == 7) {
            var other_set = load_bag("bag7", current_active_vertices);
            other_set.show_information();
            for (var tree : solutions) {
                for (var other_tree : solutions.get_compatible_trees(tree)) {
                    if (tree == other_tree)
                        continue;
                    var result = tree.test_equivalency_old_v2(other_tree);
                    if (result == RootedTree.RootedTreeRelation.WORSE || result == RootedTree.RootedTreeRelation.EQUIVALENT) {
                        equivalent_solutions.add(new Utilities.Pair<>("Dominated", tree));
                        equivalent_solutions.add(new Utilities.Pair<>("Dominating", other_tree));
                        System.out.println(tree.test_equivalency(other_tree));
                        return solutions;
                    }
                }
            }
            System.out.println(equivalent_solutions.size());
            export_bag(solutions, bag_id);
            System.out.println("Solution is present: " + solution_is_present(bag_id, solutions, ntdbag.contained_vertices));
        }
        return solutions;
    }

    /*
     * Updates the current set of partial decompositions by considering the new_tree. If there is already an equivalent
     * tree in partial_decompositions, discards the tree. If there are worse partial decompositions in there, discards
     * them instead.
     */
    public static void update_partial_decompositions(RootedTree new_tree, RootedTreeSet partial_decompositions) {
        boolean is_new = true;
        Set<RootedTree> to_remove = new HashSet<>();
        for (var other_tree : partial_decompositions.get_compatible_trees(new_tree)) {
            switch (other_tree.test_equivalency(new_tree)) {
                case BETTER:
                case EQUIVALENT:
                    is_new = false;
                    break;
                case WORSE:
                    to_remove.add(other_tree);
                default:
            }
        }
        if (is_new) {
            partial_decompositions.insert_tree(new_tree);
            for (var other_tree : to_remove) {
                partial_decompositions.remove_tree(other_tree);
            }
        }
    }
    private boolean solution_is_present(int bag_id, RootedTreeSet partial_decompositions, Set<Integer> active_vertices) {
        if (!Solver.DEBUG_MODE)
            return true;
        System.out.println("checking whether solution is present in " + bag_id);
        try {
            var tree = GraphReader.read_adjacency_file(SOLUTION_FOLDER + "/tree_" + bag_id + ".txt");
            tree.active_vertices = active_vertices;
            tree = tree.forget_other(active_vertices);
            for (var present : partial_decompositions) {
                var result = tree.test_equivalency_old_v2(present);
//                System.out.println(result);
                if (result == RootedTree.RootedTreeRelation.EQUIVALENT) {
                    equivalent_solutions.add(new Utilities.Pair<>("equivalent tree at " + bag_id, present));
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    /*
     * Checks whether a tree is a treedepth decomposition of the subgraph induced by all vertices contained in bags
     * rooted in the bag with bag_id in the tree decomposition.
     */
    private boolean is_partial_solution(int bag_id, RootedTree tree) {
        var contained = ntd.contained_in_subtree.get(bag_id);
        var parent_mapping = tree.export();
        parent_mapping.put(tree.alive_children.get(-1).stream().findFirst().get(), -1);
        for (int vertex : contained) {
            for (int adjacent : graph.adjacency_list.get(vertex)) {
                if (!contained.contains(adjacent) || vertex > adjacent) continue;
                if (!verify_edge(vertex, adjacent, parent_mapping) && !verify_edge(adjacent, vertex, parent_mapping)) {
                    System.out.println("Edge from " + vertex + " to " + adjacent + " is missing!");
                    return false;
                }
            }
        }
        return true;
    }

    private void export_history(RootedTree tree, String counter) throws IOException {
        Deque<RootedTree> trees = new ArrayDeque<>();
        trees.add(tree);
        while (!trees.isEmpty()) {
            var current = trees.pop();
            new File("partials" + counter).mkdirs();
            GraphWriter.save_tree_as_adjacency(current, "partials" + counter + "/tree_" + current.history.bag_id + ".txt");
            if (current.history.p1 != null) {
                trees.add(current.history.p1);
            }
            if (current.history.p2 != null) {
                trees.add(current.history.p2);
            }
        }
    }

    private void export_bag(RootedTreeSet bag, int bag_id) throws IOException {
        int tree_id = 0;
        for (var tree : bag) {
            new File("partials/bag" + bag_id).mkdirs();
            GraphWriter.save_tree_as_adjacency(tree, "partials/bag" + bag_id + "/tree_" + tree_id + ".txt");
            tree_id++;
        }
    }

    private RootedTreeSet load_bag(String path, Set<Integer> active_vertices) throws IOException {
        RootedTreeSet ret = new RootedTreeSet();
        int tree_id = 0;
        while ((new File(path + "/tree_" + tree_id + ".txt")).exists()) {
            var tree = GraphReader.read_adjacency_file(path + "/tree_" + tree_id + ".txt");
            tree.active_vertices = active_vertices;
            tree = tree.forget_other(active_vertices);
            ret.insert_tree(tree);
            tree_id++;
        }
        return ret;
    }

    private boolean verify_edge(int start, int to, Map<Integer, Integer> parent_mapping) {
        if (start == to)
            return true;
        var current = start;
        while (parent_mapping.containsKey(current)) {
            current = parent_mapping.get(current);
            if (current == to) {
                return true;
            }
        }
        current = to;
        while (parent_mapping.containsKey(current)) {
            current = parent_mapping.get(current);
            if (current == start) {
                return true;
            }
        }
        return false;
    }

    public boolean verify_solution(RootedTree solution) {
        if (solution == null) {
            return false;
        }
        var tree = solution.export();
        for (int from = 0; from < graph.vertices; ++from) {
            for (var to : graph.adjacency_list.get(from)) {
                if (!verify_edge(from, to, tree)) {
                    System.out.printf("Edge (%d, %d) is missing!\n", from, to);
                    return false;
                }
            }
        }
        System.out.println("The solution is in fact a treedepth decomposition.");
        return true;
    }


    public void solve() throws IOException {
        if (results == null) {
            VertexDomination.update_domination(graph);
            equivalent_solutions = new ArrayList<>();
//            results = solve(ntd.root);
            results = solve(ntd.root);
        } else {
            return;
        }
        for (var tree : results) {
            if (solution == null || tree.height_mapping.get(-1) < solution.height_mapping.get(-1)) {
                solution = tree;
            }
        }

        if (DEBUG_MODE) {
            gp = new GraphPainter();
//            var solution_tree = GraphReader.read_adjacency_file("partials/tree_17.txt");
//            solution_tree.active_vertices = solution.active_vertices;
//            solution_tree = solution_tree.forget_other(current_active_vertices);
//            current_active_vertices = new HashSet<>(Arrays.asList(2, 5));
//            gp.add_graph("solution", forgotten_tree);
//            for (var tree : results) {
//                System.out.println(tree.test_equivalency(solution_tree));
//                var equivalence = tree.better_equivalency(solution_tree);
//                if (equivalence == RootedTree.RootedTreeRelation.EQUIVALENT) {
//                    tree.better_equivalency(solution_tree);
//                }
//                System.out.println(equivalence);
//                gp.add_graph("result" + equivalence, tree);
//                var forgotten = tree.forget_other(current_active_vertices);
//                gp.add_graph("forgotten", forgotten);
//                System.out.println(forgotten_tree.better_equivalency(forgotten));
//                System.out.println("--");
//            }
//            var forgotten_tree = GraphReader.read_adjacency_file("partials/tree_43.txt");
//            Solver.current_active_vertices = new HashSet<>(Arrays.asList(1, 6, 19));
//            forgotten_tree.active_vertices = current_active_vertices;
//            forgotten_tree = forgotten_tree.forget_other(current_active_vertices);
//            gp.add_graph("solution", forgotten_tree);
            for (var p : equivalent_solutions) {
                gp.add_graph_with_history(p.first, p.second);
////                var result = p.second.better_equivalency(forgotten_tree);
////                gp.add_graph(result.toString(), p.second);
////                if (result == RootedTree.RootedTreeRelation.BETTER) {
////                    p.second.better_equivalency(forgotten_tree);
////
////                }
//                var result1 = forgotten_tree.better_equivalency(p.second);
//                var result2 = p.second.better_equivalency(forgotten_tree);
//                if (result1 != result2) {
//                    System.out.println(result1 + ", " +  result2);
//                }
//                if (result1 == RootedTree.RootedTreeRelation.EQUIVALENT || result1 == RootedTree.RootedTreeRelation.WORSE) {
//                    gp.add_graph(result1.toString(), p.second);
//                }

            }
            if (solution != null) {
                export_history(solution, "");
                gp = new GraphPainter();
                gp.add_graph_with_history("result_tree", solution);
                int i = 0;
                for (var other : results) {
                    if (other == solution)
                        continue;
                    if (i > 20)
                        break;
                    gp.add_graph("alternative solution (" + (i + 1) + ")", other);
                    export_history(other, "" + (i + 1));
                    ++i;
                }
                GraphWriter.save_tree_as_adjacency(solution, "solution.txt");
                export_history(solution, "");
            }
        }
    }

    public RootedTree get_minimal_depth_solution() {
        if (results.size() == 0) {
            return null;
        }
        return solution;
    }

}
