package io;

import util.Graph;
import util.RootedTree;
import util.TreeDecomposition;
import util.VertexBag;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
public class GraphReader{

    private static String next_non_comment(BufferedReader reader) throws java.io.IOException{
        String line = reader.readLine();
        while (line.startsWith("c")) {
            line = reader.readLine();
        }
        return line;
    }

    public static Graph read_graph(String filename) throws java.io.IOException {
        return read_graph(new FileInputStream(filename));
    }


    public static Graph read_graph(InputStream input_stream) throws java.io.IOException {
        var reader = new BufferedReader(new InputStreamReader(input_stream, StandardCharsets.UTF_8), 10240);
        String header = next_non_comment(reader);
        var words = header.split(" ");
        System.out.println(header);
        int vertices = Integer.parseInt(words[2]);
        int edges = Integer.parseInt(words[3]);
        List<List<Integer>> adjacency_list = new ArrayList<>();

        for (int i = 0; i < vertices; ++i) {
            adjacency_list.add(new ArrayList<>());
        }

        for (int i = 0; i < edges; ++i) {
            var edge = next_non_comment(reader).split(" ");
            int from = Integer.parseInt(edge[0]) - 1;
            int to = Integer.parseInt(edge[1]) - 1;
            adjacency_list.get(from).add(to);
            adjacency_list.get(to).add(from);
        }

        return new Graph(vertices, adjacency_list);
    }

    public static TreeDecomposition read_tree_decomposition(String filename) throws java.io.IOException {
        return read_tree_decomposition(new FileInputStream(filename));
    }

    public static TreeDecomposition read_tree_decomposition(InputStream input_stream) throws java.io.IOException {
        var reader = new BufferedReader(new InputStreamReader(input_stream, StandardCharsets.UTF_8), 10240);
        String header = next_non_comment(reader);
        var words = header.split(" ");
        int bags = Integer.parseInt(words[2]);
        int edges = bags - 1;
        List<VertexBag> bags_list = new ArrayList<>();
        int roots = 0;
        for (int i = 0; i < bags; ++i) {
            String this_bag = next_non_comment(reader);
            words = this_bag.split(" ");
            if (words[0].equals("br"))
                roots++;
            bags_list.add(new VertexBag(Arrays.stream(words).skip(2).map(x -> Integer.parseInt(x) - 1).collect(Collectors.toSet())));
        }
        if (roots != 1) {
            System.out.println("This tree decomposition has " + roots + " roots!");
        }
        Map<Integer, Integer> parent_mapping = new HashMap<>();
        for (int i = 0; i < edges; ++i) {
            var this_edge = next_non_comment(reader).split(" ");
            int parent = Integer.parseInt(this_edge[0]) - 1;
            int child = Integer.parseInt(this_edge[1]) - 1;
            parent_mapping.put(child, parent);
        }

        return new TreeDecomposition(bags_list, parent_mapping);
    }

    // Format:
    // first line contains number of vertices as well as a list of roots (all space separated)
    // each following line contains the children of the i-th vertex (starting with i = 0)
    public static RootedTree read_adjacency_file(String filename) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8), 10240);
        var header = reader.readLine();
//        System.out.println(header);
        var values = header.split(" ");
        var vertex_num = Integer.parseInt(values[0]);
        Set<Integer> roots = new HashSet<>();
        for (var index = 1; index < values.length; ++index) {
            roots.add(Integer.parseInt(values[index]));
        }
        List<List<Integer>> adjacency = new ArrayList<>();
        for (int i = 0; i < vertex_num; ++i) {
            var current = new ArrayList<Integer>();
            var line = reader.readLine();
            adjacency.add(current);
            if (line == null || line.equals("")) {
                continue;
            }
            for (var vertex : line.split(" ")) {
                current.add(Integer.parseInt(vertex));
            }
        }
        return new RootedTree(adjacency, roots);

    }
}
