package io;

import util.RootedTree;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public class GraphWriter {

    public static void save_tree(RootedTree tree, String filename) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        writer.write(String.valueOf(tree.height_mapping.get(-1) - 1));
        writer.newLine();
        var parent_mapping = tree.export();
        for (int i = 0; i < parent_mapping.keySet().size() + 1; ++i) {
            if (!parent_mapping.containsKey(i)) {
                writer.write('0');
                writer.newLine();
                continue;
            }
            writer.write(String.valueOf((parent_mapping.get(i) + 1)));
            writer.newLine();
        }
        writer.close();
    }

    public static void save_tree_as_adjacency(RootedTree tree, String filename) throws IOException {
        List<List<Integer>> adjacency = new ArrayList<>();
        var mapping = tree.export();
        var max = mapping.keySet().stream().reduce(0, BinaryOperator.maxBy(Integer::compareTo));
        max = Math.max(max, tree.height_mapping.keySet().stream().reduce(0, BinaryOperator.maxBy(Integer::compareTo)));
        for (int current = 0; current <= max; ++current) {
            adjacency.add(new ArrayList<>());
        }
        for (var vertex : mapping.keySet()) {
            var parent = mapping.get(vertex);
            adjacency.get(parent).add(vertex);
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        writer.write(String.valueOf(max + 1) + " " + tree.alive_children.get(-1).stream().map(String::valueOf).collect(Collectors.joining(" ")));
        writer.newLine();
        for (int current = 0; current <= max; ++current) {
            writer.write(adjacency.get(current).stream().map(String::valueOf).collect(Collectors.joining(" ")));
            writer.newLine();
        }
        writer.close();
    }
}
