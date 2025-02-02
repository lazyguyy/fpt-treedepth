package testing;

import io.GraphReader;

import java.io.IOException;

public class TreeDecompositionTest {
    public static void main(String[] args) throws IOException {
        var graph = GraphReader.read_graph("test_graph.txt");
        var ntd = GraphReader.read_tree_decomposition("test_graph.ntd");
        ntd.display(ntd.root, "");
        ntd.reorder(graph);
        System.out.println("Reordered");
        ntd.display(ntd.root, "");
    }

}
