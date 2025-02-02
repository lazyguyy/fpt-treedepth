package util;

import java.util.Set;

public class VertexBag {
    public Set<Integer> contained_vertices;
    public BagType type;

    public VertexBag(Set<Integer> contained_vertices) {
        this.contained_vertices = contained_vertices;
    }

    public void set_type(BagType type) {
        this.type = type;
    }

    public int size() {
        return contained_vertices.size();
    }

    public static enum BagType {
        LEAF, INSERT, JOIN, FORGET;
    }
}
