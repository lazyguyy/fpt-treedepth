package testing;


import io.GraphReader;
import jdk.jshell.execution.Util;
import solver.Solver;
import util.Graph;
import util.RootedTree;
import util.RootedTreeSet;
import util.Utilities;

import java.util.*;
import java.util.stream.Collectors;

public class RootedTreeTest {

    public static void main(String[] args) throws Exception {
        var painter = new GraphPainter();
        ArrayList<RootedTree> trees = new ArrayList<>();
        RootedTreeSet tree_set = new RootedTreeSet();
        var active_vertices = new HashSet<Integer>(Arrays.asList(0, 1, 3, 4));
        int total_trees = 2;
        var first_tree = GraphReader.read_adjacency_file("tests/tree1.txt");
        var second_tree = GraphReader.read_adjacency_file("tests/tree2.txt");
        first_tree.active_vertices = active_vertices;
        second_tree.active_vertices = active_vertices;
        first_tree = first_tree.forget_other(active_vertices);
        second_tree = second_tree.forget_other(active_vertices);
        second_tree.rename_vertex(2, 5);
        painter.add_graph("first", first_tree);
        painter.add_graph("second", second_tree);
        System.out.println(first_tree.join_v3(second_tree, 5).size());


//        System.out.println(trees[0].test_equivalency(trees[1]));
        // test merge operation
//        var merged = trees[0].advanced_join(trees[1], 7);

    }
}
