package util;

import java.util.List;

public class Graph {
    public final int vertices;
    public List<List<Integer>> adjacency_list;

    public Graph(int vertices, List<List<Integer>> adjacency_list) {
        this.vertices = vertices;
        this.adjacency_list = adjacency_list;
    }
}
