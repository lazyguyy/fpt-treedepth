package testing;

import io.GraphReader;
import util.RootedTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SolutionVisualizer {

    public static void main(String[] args) {
        var painter = new GraphPainter();

        File folder = new File("partials/");
        List<RootedTree> trees = new ArrayList<>();
        for (var file : Objects.requireNonNull(folder.list())) {
            try {
                var tree = GraphReader.read_adjacency_file("partials/"  + file);
                if (tree.height_mapping.containsKey(172) || tree.height_mapping.containsKey(198)) {
                    painter.add_graph(file, tree);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
