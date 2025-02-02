package util;

// Possible insidious programming mistakes.
// forgot to query structure with get_structure() and instead work with a potential nullptr or the wrong structure
// second case shouldn't be possible but you never know ;)


import solver.Solver;
import testing.GraphPainter;

import java.util.*;


public class RootedTree {

    // In this tree the root vertex will have label -1 that can never appear during normal execution of the algorithm
    // This virtual root makes it easier to implement the merge and insert operation

    // the active vertices of this rooted tree
    public Set<Integer> active_vertices;
    // all (active) leaves
    public Set<Integer> leaves;
    // all vertices contained in the tree

    // We are using maps here because these vertices are not necessarily
    // numbered from 0 to n - 1
    public Map<Integer, Integer> height_mapping;

    public Map<Integer, Integer> depth_map;
    public Map<Integer, Integer> parent_mapping;

    public Map<Integer, Set<Integer>> alive_children;
    public Map<Integer, List<RemovedTree>> removed_subtrees;

    private Map<Integer, Integer> structure;


    // contains additional information about direct active descendants for each inactive vertex of the advanced structure.
    private Map<Integer, Set<Integer>> enriched_structure;

    // cache key value, used for hashing and matching trees to reduce n^2 runtime on search for equivalent trees etc.
    private Integer key_value = null;

    // If this flag is set to true, then for each RootedTree a history of how it was generated will be saved.
    // Useful for debugging, however, leads to increase in memory usage in practice
    public RootedTreeHistory history;

    public RootedTree(int vertex) {

        active_vertices = new HashSet<>();
        active_vertices.add(vertex);

        parent_mapping = new HashMap<>();
        parent_mapping.put(vertex, -1);

        alive_children = new HashMap<>();
        alive_children.put(-1, new HashSet<>());
        alive_children.get(-1).add(vertex);
        alive_children.put(vertex, new HashSet<>());

        removed_subtrees = new HashMap<>();
        removed_subtrees.put(-1, new ArrayList<>());
        removed_subtrees.put(vertex, new ArrayList<>());

        leaves = new HashSet<>();
        leaves.add(vertex);

        height_mapping = new HashMap<>();
        height_mapping.put(vertex, 1);
        height_mapping.put(-1, 2);

        history = new RootedTreeHistory(null, null, OperationType.LEAF);
    }

    public RootedTree() {
        active_vertices = Solver.current_active_vertices;
        parent_mapping = new HashMap<>();
        alive_children = new HashMap<>();
        alive_children.put(-1, new HashSet<>());
        removed_subtrees = new HashMap<>();
        removed_subtrees.put(-1, new ArrayList<>());
        leaves = new HashSet<>();
        height_mapping = new HashMap<>();
        history = new RootedTreeHistory(null, null, OperationType.LEAF);
    }


    public RootedTree(List<List<Integer>> adjacency_list, Set<Integer> root) {
        this();
        active_vertices = new HashSet<>();
        var contained_vertices = build_tree_structure(adjacency_list, root);
        calculate_height_mapping(contained_vertices);
        history = new RootedTreeHistory(null, null, OperationType.LEAF);
    }

    public RootedTree(RootedTree other, OperationType type) {
        // copy everything. yes that sucks
        // TODO Perhaps active vertices and contained vertices don't have to be copied.
        this.active_vertices = Solver.current_active_vertices;
        this.height_mapping = new HashMap<>(other.height_mapping);
        this.parent_mapping = new HashMap<>(other.parent_mapping);
        this.leaves = new HashSet<>(other.leaves);
        this.alive_children = new HashMap<>();
        this.removed_subtrees = new HashMap<>();

        for (int child : height_mapping.keySet()) {
            this.alive_children.put(child, new HashSet<>(other.alive_children.get(child)));
            this.removed_subtrees.put(child, new ArrayList<>(other.removed_subtrees.get(child)));
        }

//        get_advanced_structure();
        if (Solver.DEBUG_MODE)
            this.history = new RootedTreeHistory(other, null, type);

    }

    public RootedTree(RootedTree other) {
        // copy everything. yes that sucks
        this.active_vertices = Solver.current_active_vertices;
        this.height_mapping = new HashMap<>(other.height_mapping);
        this.parent_mapping = new HashMap<>(other.parent_mapping);
        this.leaves = new HashSet<>(other.leaves);
        this.alive_children = new HashMap<>();
        this.removed_subtrees = new HashMap<>();

        for (int child : height_mapping.keySet()) {
            this.alive_children.put(child, new HashSet<>(other.alive_children.get(child)));
            this.removed_subtrees.put(child, new ArrayList<>(other.removed_subtrees.get(child)));
        }

        this.history = other.history;
        this.structure = other.structure;

    }

    public RootedTree(RootedTree other, RootedTree other2, OperationType type) {
        this(other, type);
        if (Solver.DEBUG_MODE)
            this.history = new RootedTreeHistory(other, other2, type);
    }

    public RootedTree(RootedTree parent1, RootedTree parent2, OperationType type, Map<Integer, Integer> merged_structure) {
        if (Solver.DEBUG_MODE)
            this.history = new RootedTreeHistory(parent1, parent2, type);

        var contained_vertices = build_from_merged_structure(merged_structure, parent1.active_vertices);
        calculate_height_mapping(contained_vertices);
        insert_tree_into_structure_tree(parent1);
        insert_tree_into_structure_tree(parent2);
    }

    // In contrast to the simple structure (see below), the advanced structure contains all vertices that are active
    // or have two or more children. For each vertex, its parent is saved in the structure.
    // Don't mix advanced structures and simple structure since they both save to the same variable but are different.
    // Compare structures with Utilities::compare_{simple|advanced}_structure
    public Map<Integer, Integer> get_advanced_structure() {
        if (structure != null)
            return structure;
        structure = new HashMap<>();
        var vertices = new ArrayDeque<Pair>();
        vertices.add(new Pair(-1, -1));
        while (vertices.size() > 0) {
            var current_pair = vertices.pop();
            int vertex = current_pair.first;
            int ancestor = current_pair.second;
            if (vertex != -1) {
                if (active_vertices.contains(vertex) || alive_children.get(vertex).size() > 1) {
                    structure.put(vertex, ancestor);
                    ancestor = vertex;
                }
            }
            for (var child : alive_children.get(vertex)) {
                vertices.add(new Pair(child, ancestor));
            }
        }
//        if (old_structure != null && !old_structure.equals(structure)) {
//            System.out.println("Error!");
//            return null;
//        }
        return structure;
    }

    // Only makes sense in the context of advanced structures. Every inactive vertex gets a set of its direct active
    // descendants (active descendants where there is not other active vertex between them). This information is needed
    // when merging two rooted trees. Since the number of active vertices is bounded, and the advanced structure only
    // contains inactive vertices with at least 2 children, the number of inactive vertices that have to be annotated
    // is bounded linearly and therefore the space overhead is at most quadratic in the number of active vertices.
    public Map<Integer, Set<Integer>> enrich_structure() {
        if (enriched_structure != null)
            return enriched_structure;
        var this_structure = this.get_advanced_structure();
        var direct_active_descendants = new HashMap<Integer, Set<Integer>>();
        for (var v : active_vertices) {
            var current = this_structure.get(v);
            while (!is_active(current)) {
                if (!direct_active_descendants.containsKey(current))
                    direct_active_descendants.put(current, new HashSet<>());
                direct_active_descendants.get(current).add(v);
                current = this_structure.get(current);
            }
        }
        enriched_structure = direct_active_descendants;
        return direct_active_descendants;
    }

    public Utilities.Pair<Boolean, Map<Integer, Integer>> merge_advanced_structures(RootedTree other) {
        var this_structure = this.get_advanced_structure();
        var this_annotation = this.enrich_structure();
        var other_structure = other.get_advanced_structure();
        var other_annotation = other.enrich_structure();
        var new_structure = new HashMap<Integer, Integer>();
        for (var v : active_vertices) {
            var last_vertex = v;
            var v1 = this_structure.get(v);
            var v2 = other_structure.get(v);
            while (!is_active(v1) && !is_active(v2)) {
                var this_descendants = this_annotation.get(v1);
                var other_descendants = other_annotation.get(v2);
                // TODO fix equal case
                if (this_descendants.containsAll(other_descendants)) {
                    new_structure.put(last_vertex, v2);
                    last_vertex = v2;
                    v2 = other_structure.get(v2);
                } else if (other_descendants.containsAll(this_descendants)) {
                    new_structure.put(last_vertex, v1);
                    last_vertex = v1;
                    v1 = this_structure.get(v1);
                } else {
                    // Not compatible
                    return new Utilities.Pair<>(false, null);
                }
            }
            // can only happen if the parent vertex of v is active
            if (is_active(v1) && is_active(v2)) {
                new_structure.put(v, v1);
            } else {
                // in this case only one of the two structural vertices is inactive, and we can simply insert
                // all vertices upwards from the vertex until we reach another active vertex.
                while (!is_active(v1)) {
                    new_structure.put(last_vertex, v1);
                    last_vertex = v1;
                    v1 = this_structure.get(v1);
                }
                while (!is_active(v2)) {
                    new_structure.put(last_vertex, v2);
                    last_vertex = v2;
                    v2 = other_structure.get(v2);
                }
                // both v1 and v2 should be the same active vertex.
                new_structure.put(last_vertex, v1);
            }
        }
        return new Utilities.Pair<>(true, new_structure);
    }

    // the simple structure only contains the active vertices, and is no longer relevant.
    public Map<Integer, Integer> get_simple_structure() {
        if (structure != null)
            return structure;
        structure = new HashMap<>();
        var vertices = new ArrayDeque<Integer>();
        vertices.add(-1);
        int active_vertices_visited = 0;
        while (active_vertices_visited < active_vertices.size()) {
            int current_vertex = vertices.pop();
            if (active_vertices.contains(current_vertex)) {
                active_vertices_visited++;
            }
            // because the virtual root has no parent and thus its not possible to find a parent pointer
            if (current_vertex != -1) {
                int parent = parent_mapping.get(current_vertex);
                if (active_vertices.contains(parent) || parent == -1) {
                    structure.put(current_vertex, parent);
                } else {
                    structure.put(current_vertex, structure.get(parent));
                }
            }
            vertices.addAll(alive_children.get(current_vertex));
        }
        return structure;
    }

    /*
     * Iterate over the graph starting at the root and build parent pointers.
     * Also mark all leaves and add them to a separate list, so that we can
     * easily "restrict" the tree in the restriction operation.
     * Returns the contained vertices
     */
    private Set<Integer> build_tree_structure(List<List<Integer>> adjacency_list, Set<Integer> roots) {
        parent_mapping = new HashMap<>();
        Deque<Integer> stack = new ArrayDeque<>();
        for (var root : roots) {
            parent_mapping.put(root, -1);
            stack.push(root);
        }
        alive_children.put(-1, roots);
        leaves = new HashSet<>();

        // iterate over all vertices and count children.
        var contained_vertices = new HashSet<Integer>();
        while (!stack.isEmpty()) {
            int current = stack.pop();
            contained_vertices.add(current);
            active_vertices.add(current);

            HashSet<Integer> child_set = new HashSet<>();
            for (int neighbor : adjacency_list.get(current)) {
                if (!active_vertices.contains(neighbor)) {
                    parent_mapping.put(neighbor, current);
                    stack.push(neighbor);
                    child_set.add(neighbor);
                }
            }
            alive_children.put(current, child_set);
            removed_subtrees.put(current, new ArrayList<>());
            if (child_set.isEmpty()) {
                leaves.add(current);
            }
        }
        return contained_vertices;
    }

    private Set<Integer> build_from_merged_structure(Map<Integer, Integer> merged_structure, Set<Integer> active_vertices) {
        this.active_vertices = Solver.current_active_vertices;
        parent_mapping = new HashMap<>();
        alive_children = new HashMap<>();
        var contained_vertices = new HashSet<Integer>();
        removed_subtrees = new HashMap<>();
        leaves = new HashSet<>();
        for (var vertex : merged_structure.keySet()) {
            var parent = merged_structure.get(vertex);
            parent_mapping.put(vertex, parent);
            // catches all vertices that are not leaves
            if (!alive_children.containsKey(parent)) {
                alive_children.put(parent, new HashSet<>());
                removed_subtrees.put(parent, new ArrayList<>());
            }
            alive_children.get(parent).add(vertex);
        }
        for (var vertex : merged_structure.keySet()) {
            contained_vertices.add(vertex);
            // catches all leaves
            if (!alive_children.containsKey(vertex)) {
                leaves.add(vertex);
                alive_children.put(vertex, new HashSet<>());
                removed_subtrees.put(vertex, new ArrayList<>());
            }
        }
        return contained_vertices;
    }

    private void insert_tree_into_structure_tree(RootedTree other) {
        var structure = this.get_advanced_structure();
        for (var vertex : other.active_vertices) {
            removed_subtrees.get(vertex).addAll(other.removed_subtrees.get(vertex));
            update_heights(vertex);
            // active vertices are already part of the new tree since they are part of the structure, and so we can skip them
            while (vertex != -1) {
                var last = vertex;
                vertex = other.parent_mapping.get(vertex);
                if (other.is_active(vertex)) {
                    update_heights(last);
                    break;
                }
                // in this case it is an inactive structural vertex, since these are the only non-active vertices that should already be contained.
                if (height_mapping.containsKey(vertex)) {
                    removed_subtrees.get(vertex).addAll(other.removed_subtrees.get(vertex));
                    update_heights(last);
                    continue;
                }
                var parent = parent_mapping.get(last);
                while (!structure.containsKey(parent) && parent != -1 && height_mapping.get(parent) < other.height_mapping.get(vertex)) {
                    last = parent;
                    parent = parent_mapping.get(last);
                }
                this.copy_vertex_from_tree_and_insert_inactive(other, vertex, Collections.singletonList(last), parent);
            }
        }
    }

    /*
     * Calculates the height of the tree at every vertex.
     */
    private void calculate_height_mapping(Set<Integer> contained_vertices) {
        height_mapping = new HashMap<>();
        HashMap<Integer, Integer> child_counter = new HashMap<>();

        for (int vertex : contained_vertices) {
            child_counter.put(vertex, alive_children.get(vertex).size());
        }

        child_counter.put(-1, alive_children.get(-1).size());

        Deque<Integer> actual_leaves = new ArrayDeque<>(leaves);
        // iterate over leaves first and then the parents that have no more children etc.
        while (actual_leaves.size() > 0) {
            int current = actual_leaves.pollFirst();
            int height = removed_subtrees.get(current).stream().map(x -> x.height).reduce(0, Math::max) + 1;
            for (int child : alive_children.get(current)) {
                height = Math.max(height, height_mapping.get(child) + 1);
            }
            height_mapping.put(current, height);
            if (!parent_mapping.containsKey(current))
                continue;
            int parent = parent_mapping.get(current);
            int remaining_children = child_counter.get(parent) - 1;
            if (remaining_children == 0) {
                child_counter.remove(parent);
                actual_leaves.add(parent);
            } else {
                child_counter.put(parent, remaining_children);
            }
        }
    }


    /*
     * Updates the height for the current vertex. While the height of a vertex changes, the height of the parent will
     * also be updated.
     */
    private void update_heights(int vertex) {
        while (true) {
            int this_height = height_mapping.get(vertex);
            int new_height = 0;
            for (var child : alive_children.get(vertex)) {
                new_height = Math.max(height_mapping.get(child), new_height);
            }
            for (var removed_child : removed_subtrees.get(vertex)) {
                new_height = Math.max(removed_child.height, new_height);
            }
            new_height++;
            if (new_height != this_height) {
                height_mapping.put(vertex, new_height);
            }
            if (new_height == this_height || vertex == -1)
                break;
            vertex = parent_mapping.get(vertex);
        }
    }

    /*
     * Returns all vertices that are on the path from descendant to ancestor in this rooted tree, excluding descendant.
     * If the ancestor is not an ancestor of the descendant, then all vertices up to the root will be generated.
     */
    public List<Integer> get_vertices_on_path(int descendant, int ancestor) {
        List<Integer> vertices = new ArrayList<>();
        if (!parent_mapping.containsKey(descendant)) {
            System.out.println("Error");
        }
        descendant = parent_mapping.get(descendant);
        while (descendant != ancestor && descendant != -1) {
            vertices.add(descendant);
            descendant = parent_mapping.get(descendant);
        }
        vertices.add(descendant);
        return vertices;
    }

    /*
     * Moves the child to a different parent. Only used during insert_as_parent, so the original parent of the child
     * cannot become a leaf.
     */
    private void reattach_child(int child, int new_parent) {
        alive_children.get(parent_mapping.get(child)).remove(child);
        alive_children.get(new_parent).add(child);
        parent_mapping.put(child, new_parent);
    }

    /*
     * Whether the vertex should be treated as an active vertex (includes the special root vertex
     */
    boolean is_active(int vertex) {
        return vertex == -1 || active_vertices.contains(vertex);
    }

    /*
     * Attaches the specified vertex as a child of the parent.
     */
    private RootedTree insert_as_child(int vertex, int parent) {
        alive_children.put(vertex, new HashSet<>());
        alive_children.get(parent).add(vertex);

        active_vertices.add(vertex);
        leaves.add(vertex);
        leaves.remove(parent);

        removed_subtrees.put(vertex, new ArrayList<>());
        parent_mapping.put(vertex, parent);
        height_mapping.put(vertex, 1);

        update_heights(parent);

        return this;
    }

    /*
     * Copy the specified vertex from the RootedTree tree and insert it as the parent of the vertices in children.
     * This operation is used during the join, so the new vertex is immediately set to be inactive.
     * In addition, the removed subtrees of the vertex are copied as well.
     */
    private void copy_vertex_from_tree_and_insert_inactive(RootedTree tree, int vertex, Iterable<Integer> children) {
        insert_as_parent(vertex, children);
        active_vertices.remove(vertex);
        for (var removed_subtree : tree.removed_subtrees.get(vertex)) {
            removed_subtrees.get(vertex).add(removed_subtree);
        }
        update_heights(vertex);
    }

    // Insert vertex vertex into tree with the parent parent and the children children (must be a subset of children of parent)
    private void copy_vertex_from_tree_and_insert_inactive(RootedTree tree, int vertex, Iterable<Integer> children, int parent) {
        insert_as_parent(vertex, children, parent);
        active_vertices.remove(vertex);
        for (var removed_subtree : tree.removed_subtrees.get(vertex)) {
            removed_subtrees.get(vertex).add(removed_subtree);
        }
        update_heights(vertex);

    }

    private RootedTree insert_on_path(int vertex, int child) {
        var parent = parent_mapping.get(child);
        alive_children.put(vertex, new HashSet<>());
        alive_children.get(vertex).add(child);
        alive_children.get(parent).remove(child);
        alive_children.get(parent).add(vertex);
        removed_subtrees.put(vertex, new ArrayList<>());
        parent_mapping.put(vertex, parent_mapping.get(child));
        reattach_child(child, vertex);
        return this;

    }

    private RootedTree insert_as_parent(int vertex, Iterable<Integer> children, int parent) {
        alive_children.put(vertex, new HashSet<>());
        for (var child : children) {
            alive_children.get(vertex).add(child);
        }
        removed_subtrees.put(vertex, new ArrayList<>());
        active_vertices.add(vertex);

        int height = 0;

        for (int child : children) {
            height = Math.max(height, height_mapping.get(child) + 1);
            reattach_child(child, vertex);
        }
        height_mapping.put(vertex, height);
        parent_mapping.put(vertex, parent);
        alive_children.get(parent).add(vertex);

        update_heights(parent);

        return this;
    }

    /*
     * Insert the vertex as the parent of the vertices specified in the children list
     */
    private RootedTree insert_as_parent(int vertex, Iterable<Integer> children) {
        int parent = parent_mapping.get(children.iterator().next());
        return insert_as_parent(vertex, children, parent);
    }

    /*
     * restructures the tree so that current's parent becomes desired parent. Since all ancestor-descendant-relationships
     * have to be preserved, we need to copy some of the desired parents' predecessors as well. This is where the set
     * possible_cutting_points comes in: It contains a bunch of vertices that will be on the parent-path of current.
     */
    private void restructure_tree(int current, int desired_parent, Set<Integer> possible_cutting_points) {
        int next_vertex_ancestor = parent_mapping.get(desired_parent);
        int child_to_rearrange = desired_parent;
        possible_cutting_points = new HashSet<>(get_vertices_on_path(current, desired_parent));
        while (!possible_cutting_points.contains(next_vertex_ancestor) && !is_active(next_vertex_ancestor) && next_vertex_ancestor != -1) {
            child_to_rearrange = next_vertex_ancestor;
            next_vertex_ancestor = parent_mapping.get(next_vertex_ancestor);
        }
        int current_parent = parent_mapping.get(current);
        alive_children.get(current_parent).remove(current);
        alive_children.get(next_vertex_ancestor).remove(child_to_rearrange);

        alive_children.get(desired_parent).add(current);
        alive_children.get(current_parent).add(child_to_rearrange);

        parent_mapping.put(current, desired_parent);
        parent_mapping.put(child_to_rearrange, current_parent);
        update_heights(current_parent);
    }
    

    /*
     * Returns the relationship between first and second. May either be ANCESTOR, DESCENDANT or NEITHER
     */
    private VertexRelationship get_relationship(int first, int second) {
        var path_from_first = get_vertices_on_path(first, second);
        var last_element = path_from_first.get(path_from_first.size() - 1);
        if (last_element == second)
            return VertexRelationship.DESCENDANT;
        var path_from_second = get_vertices_on_path(second, first);
        last_element = path_from_second.get(path_from_second.size() - 1);
        if (last_element == first)
            return VertexRelationship.ANCESTOR;
        return VertexRelationship.NEITHER;
    }

    public Integer get_lca(int first, int second) {
        var path_from_first = get_vertices_on_path(first, second);
        var path_from_second = new HashSet<>(get_vertices_on_path(second, first));
        for (var vertex : path_from_first) {
            if (path_from_second.contains(vertex))
                return vertex;
        }
        return first;
    }

    public Utilities.Pair<Boolean, Integer> get_insert_point(List<Integer> vertices) {
        var ancestors_set = new HashSet<>(get_vertices_on_path(vertices.get(0), -1));
        boolean lca_set = false;
        var lca = vertices.get(0);
        for (int i = 1; i < vertices.size(); ++i) {
            int vertex = vertices.get(i);
            var this_path = new HashSet<Integer>();
            if (ancestors_set.contains(vertex)) {
                continue;
            }
            while (!ancestors_set.contains(parent_mapping.get(vertex))) {
                vertex = parent_mapping.get(vertex);
                this_path.add(vertex);
            }
            if (!lca_set && lca == vertex) {
                ancestors_set.addAll(this_path);
                lca = vertices.get(i);
                continue;
            }
            var join_point = parent_mapping.get(vertex);
            if (height_mapping.get(lca) < height_mapping.get(join_point)) {
                lca_set = true;
                lca = join_point;
            }
        }
        return new Utilities.Pair<>(!lca_set, lca);
    }

    public Integer get_lca(List<Integer> vertices) {
        vertices.sort(Comparator.comparingInt(x -> height_mapping.get(x)));
        int stop = 0;
        var height = vertices.get(0);
        while (stop < vertices.size() && height_mapping.get(vertices.get(stop)).equals(height)) {
            stop++;
        }
        var go_upwards = new HashSet<Integer>();
        for (int i = 0; i < stop; ++i) {
            go_upwards.add(vertices.get(i));
        }
        while (go_upwards.size() > 1 || stop < vertices.size()) {
            height += 1;
            var next_step = new HashSet<Integer>();
            for (var vertex : go_upwards) {
                next_step.add(parent_mapping.get(vertex));
            }
            go_upwards = next_step;
            while (stop < vertices.size() && vertices.get(stop).equals(height)) {
                go_upwards.add(vertices.get(stop));
                stop++;
            }
        }
        for (var item : go_upwards)
            return item;
        return -1;
    }

    /**
     * This function restricts the partial decomposition to a subset of vertices. Also instead of deleting the vertex,
     * we simply mark it as dead. Doing it this way removes the need of backtracking later to find the actual
     * tree decomposition and also makes debugging the algorithm much easier.
     * @param restriction the set to restrict the tree to
     * @return the restricted tree
     */
    public RootedTree forget_other(Set<Integer> restriction) {
        RootedTree restricted = new RootedTree(this, OperationType.FORGET);
        return restricted.restrict(restriction).perform_swaps();
    }

    private boolean can_swap(int vertex) {
        var parent = parent_mapping.get(vertex);
        if (is_active(parent))
            return false;
        if (alive_children.get(parent).size() > 1)
            return false;
        var subtree_height = 0;
        for (var subtree : removed_subtrees.get(parent)) {
            subtree_height = Math.max(subtree_height, subtree.height);
        }
        return subtree_height < height_mapping.get(vertex);
    }

    private boolean could_swap_if_inserted_below(int parent) {
        if (is_active(parent) || alive_children.get(parent).size() > 1)
            return false;
        var subtree_height = 0;
        for (var subtree : removed_subtrees.get(parent)) {
            subtree_height = Math.max(subtree_height, subtree.height);
        }
        var child_height = 0;
        // only one child
        for (var child : alive_children.get(parent)) {
            child_height = height_mapping.get(child);
        }
        return subtree_height <= child_height;
    }

    // returns the label of the vertex that was swapped downwards, or -1 if no swap happened or the swapped vertex became a leaf and was deleted
    private int swap_upwards(int vertex) {
        int parent = parent_mapping.get(vertex);
        if (parent == -1)
            return -1;
        int parent_parent = parent_mapping.get(parent);
        var children = alive_children.get(vertex);
        var removed_subtree = removed_subtrees.get(vertex);

        // rewrite pointers
        parent_mapping.put(vertex, parent_parent);
        parent_mapping.put(parent, vertex);
        for (var child : children) {
            parent_mapping.put(child, parent);
        }

        // in this case the inactive swapped with vertex will become part of the structure
        if (alive_children.get(vertex).size() > 1 && structure != null) {
            for (int other_vertex : structure.keySet()) {
                if (structure.get(other_vertex) == vertex) {
                    structure.replace(other_vertex, parent);
                }
            }
            structure.put(parent, vertex);
        }



        alive_children.get(parent_parent).remove(parent);
        alive_children.get(parent).remove(vertex);
        alive_children.put(vertex, new HashSet<>());
        removed_subtrees.put(vertex, new ArrayList<>());

        alive_children.get(parent_parent).add(vertex);
        alive_children.get(parent).addAll(children);
        removed_subtrees.get(parent).addAll(removed_subtree);
        height_mapping.put(vertex, height_mapping.get(vertex) + 1);

        if (!leaves.contains(vertex)) {
            alive_children.get(vertex).add(parent);
            update_heights(parent);
            return parent;
        }
        // if the vertex that was swapped upwards was a leaf vertex, we have to perform some more clean-up
        leaves.add(parent);
        var new_removed_subtree = forget_vertex(parent);
        removed_subtrees.get(vertex).add(new_removed_subtree);
        update_heights(vertex);
        return -1;
    }

    public RootedTree perform_swaps(int vertex) {
        if (!active_vertices.contains(vertex) || !Solver.DO_SWAPS) {
            return this;
        }
        while (can_swap(vertex)) {
            var swapped = swap_upwards(vertex);
            if (swapped != -1 && alive_children.get(swapped).size() == 1) {
                perform_swaps(alive_children.get(swapped).iterator().next());
            }
        }
        return this;
    }

    public RootedTree perform_swaps() {
        for (var active_vertex : active_vertices) {
            perform_swaps(active_vertex);
        }
        return this;
    }

    private RemovedTree forget_vertex(int vertex) {
        if (!leaves.contains(vertex))
            return null;

        // Remove vertex
        parent_mapping.remove(vertex);
        height_mapping.remove(vertex);
        alive_children.remove(vertex);

        // update removed subtrees
        var removed_subtree = RemovedTreeHandler.request_tree(vertex, removed_subtrees.get(vertex));
        removed_subtrees.remove(vertex);

        leaves.remove(vertex);
        return removed_subtree;
    }


    /*
     * Restricts this tree to the specified set of vertices. This is a destructive operation, hence the function
     * forget exists, which will create a copy of the tree before restricting.
     */
    private RootedTree restrict(Set<Integer> restriction) {
        Deque<Integer> to_investigate = new ArrayDeque<>();


        // Iterate over all leaves and iteratively delete inactive leaves as dead.
        for (int vertex : leaves) {
            to_investigate.push(vertex);
        }


        while (!to_investigate.isEmpty()) {
            int current = to_investigate.pollFirst();
            if (alive_children.get(current).size() == 0) {
                leaves.add(current);
                if (restriction.contains(current))
                    continue;
                var parent_vertex = parent_mapping.get(current);


                alive_children.get(parent_vertex).remove(current);

                var removed_subtree = forget_vertex(current);

                removed_subtrees.get(parent_vertex).add(removed_subtree);


                to_investigate.push(parent_vertex);
            }
        }
        active_vertices = restriction;
        return this;
    }


    /**
     * Introduces a new vertex that has to be ancestor-descendant relationships with all vertices specified in neighbors
     * into the tree. Keeps only those trees that are at most max_depth deep.
     * @param vertex The vertex to be inserted
     * @param neighbors The vertices that this vertex must be in ancestor-descendant relationships with
     * @param max_depth The maximum depth of any tree to keep
     * @return A List of all trees that we can obtain from this one by inserting the new vertex
     */
    public List<RootedTree> introduce(int vertex, List<Integer> neighbors, int max_depth) {
        Set<Integer> forbidden_children = new HashSet<>();
        // VertexDomination.forbidden_descendants has been updated previously in the solve function
        for (var other_vertex : VertexDomination.forbidden_descendants) {
            int current = other_vertex;
            if (!this.height_mapping.containsKey(other_vertex)) {
find_parent:    for (var node : removed_subtrees.keySet()) {
                    for (var removed_subtree : removed_subtrees.get(node)) {
                        if (removed_subtree.contains(other_vertex)) {
                            current = node;
                            break find_parent;
                        }
                    }
                }
            }
            while (!forbidden_children.contains(current) && current != -1) {
                forbidden_children.add(current);
                current = parent_mapping.get(current);
            }
        }
        List<RootedTree> post_insert = new ArrayList<>();
        Set<Integer> marked  = new HashSet<>();
        Set<Integer> neighbors_set = new HashSet<>(neighbors);
        int path_start;
        boolean multiple_tails = false;
        if (neighbors.size() == 0) {
            path_start = -1;
        } else {
            path_start = neighbors.get(0);
            for (int neighbor : neighbors) {
                marked.add(neighbor);
                int parent = parent_mapping.get(neighbor);
                while (!marked.contains(parent) && !neighbors_set.contains(parent) && parent != -1) {
                    marked.add(parent);
                    parent = parent_mapping.get(parent);
                }
                marked.add(parent);
                long marked_children = 0;
                for (var child : alive_children.get(parent)) {
                    marked_children += marked.contains(child) ? 1 : 0;
                }
                if (marked_children >= 2) {
                    if (!multiple_tails) {
                        path_start = parent;
                        multiple_tails = true;
                    } else {
                        if (height_mapping.get(path_start) < height_mapping.get(parent)) {
                            path_start = parent;
                        }
                    }
                }
                else if (!multiple_tails && height_mapping.get(path_start) > height_mapping.get(neighbor)) {
                    path_start = neighbor;
                }
            }
        }
        // now we generate all trees where we insert the new vertex above the start of the path component.
        int current = path_start;
        boolean path_ends_by_forbidden_ancestor = false;
        while (current != -1) {
            current = parent_mapping.get(current);
            if (VertexDomination.forbidden_ancestors.contains(current)) {
                path_ends_by_forbidden_ancestor = true;
                path_start = current;
            }
        }
        current = path_start;
        // -1 must be the root vertex at all points, so there is no point in inserting something as the parent of -1
        while (current != -1 && !forbidden_children.contains(current)) {
            // There are many ways to insert a vertex v as a child of another vertex u since we can choose whether
            // the parent pointers of uninvolved children can either point to their original parent u or to v; after
            // the deletion of v they will all point to u again. So we need to generate all possible subsets of children
            // of u.
            final int definite_child = current;
            var parent = parent_mapping.get(current);
            if (could_swap_if_inserted_below(parent)) {
                current = parent;
                continue;
            }

            var uninvolved_vertices = new ArrayList<Integer>();
            for (var child : alive_children.get(parent_mapping.get(current))) {
                if (child != definite_child && !forbidden_children.contains(child)) {
                    uninvolved_vertices.add(child);
                }
            }
//            List<Integer> uninvolved_vertices = alive_children.get(parent_mapping.get(current))
//                    .stream()
//                    .filter(x -> x != definite_child && !forbidden_children.contains(x))
//                    .collect(Collectors.toList());
            for (var subset : Utilities.generate_all_subsets(uninvolved_vertices)) {
                subset.add(definite_child);
                var inserted = new RootedTree(this, OperationType.INSERT).insert_as_parent(vertex, subset);
                if (inserted.height_mapping.get(-1) <= max_depth + 1) {
//                    inserted = inserted.perform_swaps();
                    post_insert.add(inserted);
                }
            }
            current = parent;
        }

        // if the structure of the vertices is just a path, then we can insert the new vertex anywhere below path start
        if (!multiple_tails && !path_ends_by_forbidden_ancestor) {

            Deque<Integer> insertion_points = new ArrayDeque<>();
            insertion_points.add(path_start);

            while (!insertion_points.isEmpty()) {
                current = insertion_points.pollFirst();
                insertion_points.addAll(alive_children.get(current));
                if (could_swap_if_inserted_below(current))
                    continue;
                if (VertexDomination.forbidden_ancestors.contains(current)) {
                }
                var current_children = alive_children.get(current);
                var okay_children = new HashSet<Integer>();
                for (var child : current_children) {
                    if (!forbidden_children.contains(child)) {
                        okay_children.add(child);
                    }
                }
                for (var subset : Utilities.generate_all_subsets(okay_children)) {
                    RootedTree inserted;
                    if (subset.size() == 0) {
                        inserted = new RootedTree(this, OperationType.INSERT).insert_as_child(vertex, current);
                    }
                    else {
                        inserted = new RootedTree(this, OperationType.INSERT).insert_as_parent(vertex, subset);

                    }
                    if (inserted.height_mapping.get(-1) <= max_depth + 1) {
//                        inserted = inserted.perform_swaps();
                        post_insert.add(inserted);
                    }
                }
            }
        }
        else if (!path_ends_by_forbidden_ancestor && !could_swap_if_inserted_below(path_start)){
            var containing_partition = new ArrayList<Integer>();
            var remaining_partition = new ArrayList<Integer>();

            for (var child : alive_children.get(path_start)) {
                if (marked.contains(child)) {
                    containing_partition.add(child);
                } else {
                    remaining_partition.add(child);
                }
            }
            if (Utilities.intersection_size(containing_partition, forbidden_children) > 0) {
                return post_insert;
            }
            var other_children = new HashSet<Integer>();
            for (var rem : remaining_partition) {
                if (!forbidden_children.contains(rem)) {
                    other_children.add(rem);
                }
            }
            for (var subset : Utilities.generate_all_subsets(other_children)) {
                subset.addAll(containing_partition);
                var inserted = new RootedTree(this, OperationType.INSERT).insert_as_parent(vertex, subset);
                if (inserted.height_mapping.get(-1) <= max_depth + 1) {
//                    inserted = inserted.perform_swaps();
                    post_insert.add(inserted);
                }
            }
        }
        return post_insert;
    }

    // The most recent version of the join as detailed in the paper
    public Set<RootedTree> join_v3(RootedTree other, int max_depth) {
        if (!Utilities.share_simple_structure_on_advanced_map(this, other, this.active_vertices))
            return new HashSet<>();

        var structure_merge_result = merge_advanced_structures(other);
        if (!structure_merge_result.first)
            return new HashSet<>();
        var merged_tree = new RootedTree(this, other, OperationType.JOIN, structure_merge_result.second);
        if (merged_tree.height_mapping.get(-1) > max_depth + 1)
            return new HashSet<>();
        return new HashSet<>(Collections.singletonList(merged_tree.perform_swaps()));

    }

    // Returns only a single joined tree, but only very loose dominance definition (previous version)
    public Set<RootedTree> join_v2(RootedTree other, int max_depth) {
        // first compare structure of the two trees; if it is different, don't merge them!
        if (!Utilities.share_advanced_structure(this, other, active_vertices).first)
            return new HashSet<>();
//        System.out.println("Merging...");
        // Else we can generate a singular *best* tree.
        // For this we iterate over the structural vertex pairs and merge the vertices on the path between them.
        // We always put the vertex associated with a lower height first.
        var queue = new ArrayDeque<Pair>();
        var seen_pairs = new HashSet<Pair>();
        for (var leaf : leaves){
            var new_pair = new Pair(leaf, leaf);
            queue.add(new_pair);
            seen_pairs.add(new_pair);
        }

        var merged_tree = new RootedTree(this, other, OperationType.JOIN);
        // copy over all removed subtrees that are attached to active vertices because these wont be handled during the merge.
        for (var active_vertex : active_vertices) {
            for (var removed_subtree : other.removed_subtrees.get(active_vertex)) {
                merged_tree.removed_subtrees.get(active_vertex).add(removed_subtree);
            }
        }
        // take a pair of vertices and merge upwards until the next structural relevant vertex appears
        while (!queue.isEmpty()) {
            var current = queue.pop();
            var first_vertex = current.first;
            var other_vertex = current.second;
            if (first_vertex == -1)
                continue;
            var first_parent = structure.get(first_vertex);
            var other_parent = other.structure.get(other_vertex);
            var new_pair = new Pair(first_parent, other_parent);
            if (!seen_pairs.contains(new_pair)) {
                queue.add(new_pair);
                seen_pairs.add(new_pair);
            }
            var current_insertion_point = first_vertex;
            // for merges higher up in the tree in case two inactive vertices where structurally relevant and the upper
            // vertex of the other tree was merged above the upper vertex of the copied tree.
            if (merged_tree.parent_mapping.get(current_insertion_point) == other_vertex) {
                current_insertion_point = other_vertex;
            }
            // insert all vertices of the other tree one by one
            for (var to_insert : other.get_vertices_on_path(other_vertex, other_parent)) {
                // walk upwards in the tree where we insert the vertices
                while (!merged_tree.parent_mapping.get(current_insertion_point).equals(first_parent) && merged_tree.height_mapping.get(current_insertion_point) < other.height_mapping.get(to_insert)) {
                    current_insertion_point = merged_tree.parent_mapping.get(current_insertion_point);
                }
                if (merged_tree.height_mapping.containsKey(to_insert) || to_insert == -1)
                    continue;
                int parent = merged_tree.parent_mapping.get(current_insertion_point);
//                if (parent == parent_mapping.get(current_insertion_point)) {
//                    System.out.println("self loop");
//                }
                // insert other end of path in case it is an inactive vertex
                if (to_insert.equals(other_parent)) {
                    if (merged_tree.height_mapping.get(first_parent) > other.height_mapping.get(other_parent)) {
                        // this vertex should be inserted above
                        merged_tree.copy_vertex_from_tree_and_insert_inactive(other, other_parent, alive_children.get(first_parent), first_parent);
                    } else {
                        // other vertex should be inserted above
                        merged_tree.copy_vertex_from_tree_and_insert_inactive(other, other_parent, Collections.singletonList(first_parent), merged_tree.parent_mapping.get(first_parent));
                    }
                } else {
                    merged_tree.copy_vertex_from_tree_and_insert_inactive(other, to_insert, Collections.singletonList(current_insertion_point), parent);
                    current_insertion_point = to_insert;
                }
                merged_tree.update_heights(to_insert);
                if (merged_tree.height_mapping.get(-1) > max_depth + 1) return new HashSet<>();
            }
        }
        return new HashSet<>(Collections.singletonList(merged_tree));
    }


    /**
     * Joins this RootedTree with the RootedTree other and keeps only those that are at most max_depth deep.
     * @param other the other RootedTree to perform the merge with
     * @param max_depth the maximum depth that any tree to be kept may have
     * @return A Set containing all possible joined trees
     */
    // Most basic version, tries to find every joined tree
    public Set<RootedTree> join_v1(RootedTree other, int max_depth) {
        // first compare structure of the two trees; if it is different, don't merge them!
        if (!Utilities.share_simple_structure(this, other, active_vertices)) {
//            System.out.println("Structure doesnt match");
            return new HashSet<>();
        }
        Set<RootedTree> merged_trees = new HashSet<>();
        // we will now iteratively merge all paths between active vertices. Before any merges occur, the merged tree
        // looks just like one of the original trees, in this case, the second one.
        merged_trees.add(new RootedTree(other, this, OperationType.JOIN));

        // now we need to generate all possible merges.
        for (int active_vertex : active_vertices) {
            List<Integer> these_vertices = get_vertices_on_path(active_vertex, structure.get(active_vertex));
            List<Integer> other_vertices = other.get_vertices_on_path(active_vertex, structure.get(active_vertex));

            Set<Integer> partial_merge_vertices = new HashSet<>();
            partial_merge_vertices.addAll(these_vertices);
            partial_merge_vertices.addAll(other_vertices);

            Set<RootedTree> next_level_merges = new HashSet<>();
            for (var merged_tree : merged_trees) {
                merged_tree.removed_subtrees.get(active_vertex).addAll(removed_subtrees.get(active_vertex));
                merged_tree.update_heights(active_vertex);

                var merges = Utilities.generate_all_merges(these_vertices.size() - 1, other_vertices.size() - 1);
perform_merge:  for (var merge_schema : merges) {
                    var partial_merge = new RootedTree(merged_tree);

                    // The merges are generated in an implicit format to save space, instead of directly providing
                    // the merged list, it instead provides a list of the type [x_1, x_2, x_3,...,x_n+1]
                    // where x_i is the number of vertices of the first path to insert directly before the i-th
                    // vertex of the second path.
                    int this_index = 0;
                    int other_index = 0;
                    int next_break = merge_schema.get(0);
                    int current = active_vertex;
                    while (other_index < merge_schema.size() && this_index < these_vertices.size() - 1) {
                        int vertex_to_insert;
                        if (this_index == next_break) {
                            vertex_to_insert = other_vertices.get(other_index);
                            other_index++;
                            next_break += merge_schema.get(other_index);
                        } else {
                            vertex_to_insert = these_vertices.get(this_index);
                            this_index++;
                        }
                        // this can be more efficient because in certain scenarios we can infer certain things about
                        // the vertices that have already been inserted.
                        if (partial_merge.height_mapping.containsKey(vertex_to_insert)) {
                            switch (partial_merge.get_relationship(current, vertex_to_insert)) {
                                case ANCESTOR:
                                    // In this case current is an ancestor of the next vertex on the path; a merge is
                                    // then not possible
                                    break perform_merge;
                                case NEITHER:
                                    // Here we might need to move the subtree containing the current vertex to be inserted
                                    // to some place lower in the tree
                                    partial_merge.restructure_tree(current, vertex_to_insert, partial_merge_vertices);
                                default:
                                    partial_merge.update_heights(vertex_to_insert);
                            }
                        } else {
                            partial_merge.copy_vertex_from_tree_and_insert_inactive(this, vertex_to_insert, Collections.singletonList(current));
                        }
                        current = vertex_to_insert;
                    }
                    if (partial_merge.height_mapping.get(-1) > max_depth + 1)
                        continue;
                    boolean is_new = true;
                    Set<RootedTree> to_remove = new HashSet<>();
check_duplicates:   for (var other_merge : next_level_merges) {
                        switch (other_merge.test_equivalency(partial_merge)){
                            case BETTER:
                            case EQUIVALENT:
                                is_new = false;
                                break check_duplicates;
                            case WORSE:
                                to_remove.add(other_merge);
                            default:
                        }
                    }
                    if (is_new) {
                        next_level_merges.add(partial_merge);
                        for (var other_merge : to_remove) {
                            next_level_merges.remove(other_merge);
                        }
                    }
                }
            }
            merged_trees = next_level_merges;
        }
        return merged_trees;
    }

    /**
     * Used for testing. Change the label of the specified vertex (if possible, i.e. there is a vertex with the label
     * before and none that is called after)
     * @param before The label of the vertex to be renamed
     * @param after The new label of the vertex
     * @return the tree with the renamed vertex
     */
    public RootedTree rename_vertex(int before, int after) {
        if (!height_mapping.containsKey(before) || height_mapping.containsKey(after)) {
            return this;
        }

        height_mapping.put(after, height_mapping.get(before));
        height_mapping.remove(before);

        int parent = parent_mapping.get(before);
        parent_mapping.remove(before);
        parent_mapping.put(after, parent);

        alive_children.get(parent).remove(before);
        alive_children.get(parent).add(after);

        removed_subtrees.put(after, removed_subtrees.get(before));
        removed_subtrees.remove(before);

        for (int child : alive_children.get(before)) {
            parent_mapping.put(child, after);
        }
        alive_children.put(after, alive_children.get(before));
        alive_children.remove(before);

        if (active_vertices.contains(before)) {
            active_vertices.remove(before);
            active_vertices.add(after);
        }

        return this;
    }

    public Map<Integer, Integer> get_depth_map() {
        if (depth_map == null) {
            depth_map = new HashMap<>();
            depth_map.put(-1, 1);
            var deque = new ArrayDeque<Integer>(alive_children.get(-1));
            while (!deque.isEmpty()) {
                var current = deque.pop();
                deque.addAll(alive_children.get(current));
                depth_map.put(current, depth_map.get(parent_mapping.get(current)) + 1);
            }
        }
        return depth_map;
    }

    public RootedTreeRelation test_equivalency_old_v2(RootedTree other) {
        var this_structure = get_advanced_structure();
        var comparison_result = Utilities.share_advanced_structure(this, other, this.active_vertices);
        if (!comparison_result.first)
            return RootedTreeRelation.DIFFERENT;
        var this_depth = get_depth_map();
        var other_depth = other.get_depth_map();
        var relation = RootedTreeRelation.EQUIVALENT;
        var mapping = comparison_result.second;
        for (var vertex : this_structure.keySet()) {
            RootedTreeRelation this_step_result = RootedTreeRelation.EQUIVALENT;
            var mapped_to = mapping.get(vertex);
            if (vertex == -1)
                continue;
            if (this_depth.get(vertex) < other_depth.get(mapped_to)) {
                if (this_depth.get(vertex) + height_mapping.get(vertex) <= other_depth.get(mapped_to) + other.height_mapping.get(mapped_to)) {
                    this_step_result = RootedTreeRelation.BETTER;
                } else {
                    return RootedTreeRelation.DIFFERENT;
                }
            }
            if (this_depth.get(vertex).equals(other_depth.get(mapped_to))) {
                if (height_mapping.get(vertex) < other.height_mapping.get(mapped_to)) {
                    this_step_result = RootedTreeRelation.BETTER;
                } else if (height_mapping.get(vertex) > other.height_mapping.get(mapped_to)) {
                    this_step_result = RootedTreeRelation.WORSE;
                }
            }
            if (this_depth.get(vertex) > other_depth.get(mapped_to)) {
                if (this_depth.get(vertex) + height_mapping.get(vertex) >= other_depth.get(mapped_to) + other.height_mapping.get(mapped_to)) {
                    this_step_result = RootedTreeRelation.WORSE;
                } else {
                    return RootedTreeRelation.DIFFERENT;
                }
            }
            if (relation == RootedTreeRelation.BETTER && this_step_result == RootedTreeRelation.WORSE)
                return RootedTreeRelation.DIFFERENT;
            if (relation == RootedTreeRelation.WORSE && this_step_result == RootedTreeRelation.BETTER)
                return RootedTreeRelation.DIFFERENT;
            if (this_step_result != RootedTreeRelation.EQUIVALENT)
                relation = this_step_result;
//            if ((this_depth.get(vertex) < other_depth.get(mapped_to) &&
//                    this_depth.get(vertex) + height_mapping.get(vertex) <= other_depth.get(mapped_to) + other.height_mapping.get(mapped_to)) ||
//                (this_depth.get(vertex).equals(other_depth.get(mapped_to)) &&
//                        height_mapping.get(vertex) < other.height_mapping.get(mapped_to))
//            ) {
//                if (relation == RootedTreeRelation.WORSE)
//                    return RootedTreeRelation.DIFFERENT;
//                relation = RootedTreeRelation.BETTER;
//            }
//
//
//            if ((this_depth.get(vertex) > other_depth.get(mapped_to) &&
//                    this_depth.get(vertex) + height_mapping.get(vertex) >= other_depth.get(mapped_to) + other.height_mapping.get(mapped_to)) ||
//                (this_depth.get(vertex).equals(other_depth.get(mapped_to)) &&
//                    height_mapping.get(vertex) > other.height_mapping.get(mapped_to))
//            ) {
//                if (relation == RootedTreeRelation.BETTER)
//                    return RootedTreeRelation.DIFFERENT;
//                relation = RootedTreeRelation.WORSE;
//            }
        }
        return relation;
    }

    // returns whether this is worse, better or different to other. In case of equality, better is returned
    public RootedTreeRelation test_equivalency(RootedTree other) {
        var comparison = Utilities.share_advanced_structure(this, other, this.active_vertices);
        if (!comparison.first)
            return RootedTreeRelation.DIFFERENT;
        var reverse_mapping = Utilities.invert_mapping(comparison.second);
        var other_to_this_vertex = Utilities.build_vertex_mapping(other, this, new HashMap<>(reverse_mapping));
        if (other_to_this_vertex != null) {
            var other_to_this_subtree = Utilities.build_subtree_mapping(other, this, reverse_mapping, other_to_this_vertex);
            if (other_to_this_subtree != null) {
                return RootedTreeRelation.BETTER;
            }
        }

        var this_to_other_vertex = Utilities.build_vertex_mapping(this, other, new HashMap<>(comparison.second));
        if (this_to_other_vertex != null) {
            var this_to_other_subtree = Utilities.build_subtree_mapping(this, other, comparison.second, this_to_other_vertex);
            if (this_to_other_subtree != null) {
                return RootedTreeRelation.WORSE;
            }
        }
        return RootedTreeRelation.DIFFERENT;
    }



    /**
     * Tests whether this RootedTree is equivalent to other
     * @param other the other RootedTree
     * @return whether this is equal to, better / worse than or different (incomporable) to other
     */
    public RootedTreeRelation test_equivalency_old(RootedTree other) {
        if (this.height_mapping.keySet().size() != other.height_mapping.keySet().size() || !this.active_vertices.equals(other.active_vertices)) {
            return RootedTreeRelation.DIFFERENT;
        }
        Deque<Pair> pairs = new ArrayDeque<>();
        // phi is the identity on all active vertices
        for (int x : active_vertices) {
            pairs.add(new Pair(x, x));
        }
        // try to build a consistent mapping
        Map<Integer, Integer> phi = new HashMap<>();
        while (!pairs.isEmpty()) {
            var current = pairs.pop();
            int first = current.first;
            int second = current.second;
            if (phi.containsKey(first) && phi.get(first) != second) {
                // if one vertex would need to be mapped to two different vertices
                if (phi.get(first) != second)
                    return RootedTreeRelation.DIFFERENT;
                continue;
            }
            // only one of both vertices is active / the root
            if ((active_vertices.contains(first) ^ active_vertices.contains(second)) ||
                    (first == -1 ^ second == -1))
                return RootedTreeRelation.DIFFERENT;

            // generate pair of parents if we are not currently at the root
            if (first == -1)
                continue;
            int first_parent = this.parent_mapping.get(first);
            int second_parent = other.parent_mapping.get(second);
            pairs.add(new Pair(first_parent, second_parent));
            phi.put(first, second);
        }
        // check whether the heights match at every vertex
        var current_relation = RootedTreeRelation.EQUIVALENT;
//        for (var vertex : active_vertices) {
//            System.out.print(vertex + " ");
//        }
        for (int vertex : this.height_mapping.keySet()) {
            if (vertex == -1)
                continue;
//            System.out.println(vertex + ", " + phi.get(vertex));
            if (height_mapping.get(vertex) < other.height_mapping.get(phi.get(vertex))) {
                if (current_relation == RootedTreeRelation.WORSE) {
                    return RootedTreeRelation.DIFFERENT;
                }
                current_relation = RootedTreeRelation.BETTER;
            } else if(height_mapping.get(vertex) > other.height_mapping.get(phi.get(vertex))) {
                if (current_relation == RootedTreeRelation.BETTER) {
                    return RootedTreeRelation.DIFFERENT;
                }
                current_relation = RootedTreeRelation.WORSE;
            }
        }
        return current_relation;
    }

    /*
     * Deletes a single vertex from the tree
     */
    private RootedTree delete(int vertex) {
        if (!height_mapping.containsKey(vertex) || vertex == -1) {
            return this;
        }
        RootedTree deleted = new RootedTree(this);
        int parent = parent_mapping.get(vertex);
        deleted.removed_subtrees.get(parent).addAll(removed_subtrees.get(vertex));
        deleted.alive_children.get(parent).addAll(alive_children.get(vertex));
        for (int child : alive_children.get(vertex)) {
            deleted.parent_mapping.put(child, parent);
        }
        deleted.leaves.remove(vertex);
        if (deleted.alive_children.get(parent).size() == 0) {
            deleted.leaves.add(parent);
        }
        deleted.alive_children.remove(vertex);
        deleted.removed_subtrees.remove(vertex);
        deleted.height_mapping.remove(vertex);
        deleted.alive_children.get(parent).remove(vertex);
        deleted.active_vertices.remove(vertex);
        deleted.update_heights(parent);
        return deleted;
    }

    /**
     * Removes all vertices specified in the Iterable vertices. (Calculates the restriction of a tree to a subset of
     * vertices)
     * @param vertices The vertices to be deleted
     * @return The restriction of this tree to the subset of vertices not contained in vertices
     */
    public RootedTree delete_all(Iterable<Integer> vertices) {
        RootedTree deleted = new RootedTree(this);
        for (int vertex : vertices) {
            deleted = deleted.delete(vertex);
        }
        return deleted;
    }

    /**
     * Exports this rooted tree as a Map of parent pointers
     * @return A Map containing parent pointers
     */
    public Map<Integer, Integer> export() {
        Map<Integer, Integer> exported = new HashMap<>(parent_mapping);
        // should always be just one child.
        for (var child : alive_children.get(-1)) {
            exported.remove(child);
        }
        for (int vertex : height_mapping.keySet()) {
            if (vertex == -1)
                continue;
            var current_subtrees = removed_subtrees.get(vertex);
            for (var subtree : current_subtrees) {
                exported.putAll(subtree.generate_parent_mapping());
                exported.put(subtree.root, vertex);
            }
        }
        return exported;
    }

    // generates some kind of numerical value based on the active vertices making up the leaves
    // we can treat this value as a "hash" value to reduce the number of comparisons when checking for
    // equivalent trees
    public Integer getKeyValue() {
        if (key_value == null) {
            var hash = 1;
            var seen = new HashSet<Integer>();
            for (var vertex : leaves.stream().sorted().toList()) {
                while (!seen.contains(vertex) && vertex != -1) {
                    if (active_vertices.contains(vertex)) {
                        seen.add(vertex);
                        hash = 31 * hash + vertex;
                    }
                    vertex = parent_mapping.get(vertex);
                }
                hash = 31 * hash + vertex;
            }
            key_value = hash;
        }
        return key_value;
    }

    private enum VertexRelationship {
        ANCESTOR, DESCENDANT, NEITHER
    }

    public enum RootedTreeRelation {
        BETTER, WORSE, EQUIVALENT, DIFFERENT
    }

    public enum OperationType {
        FORGET, JOIN, INSERT, LEAF, SWAP
    }
    public static class RootedTreeHistory {
        public RootedTree p1;
        public RootedTree p2;
        public OperationType type;

        public int bag_id;
        public RootedTreeHistory(RootedTree p1, RootedTree p2, OperationType type) {
            this.p1 = p1;
            this.p2 = p2;
            this.type = type;
        }
    }

    private record Pair(int first, int second) {
        @Override
        public int hashCode() {
            return first * 31 + second;
        }
    }

}
