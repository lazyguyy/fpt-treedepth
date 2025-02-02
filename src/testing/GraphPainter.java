package testing;

import util.RootedTree;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class GraphPainter extends JFrame{
    private JTabbedPane graph_tabs;
    private JPanel panel;

    public GraphPainter(){
        this.setTitle("Homebrew Graph Viewer");
        this.setSize(1920, 1080);
        graph_tabs = new JTabbedPane();
        this.add(graph_tabs);

        this.setVisible(true);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void add_graph(String name, RootedTree tree) {
        var graph_panel = new TreePanel(tree);
        graph_tabs.addTab(name, graph_panel);
    }

    public void add_graph_with_history(String name, RootedTree final_tree) {
        Deque<RootedTree> history = new ArrayDeque<>();
        history.add(final_tree);
        add_graph(name, final_tree);
        while (history.size() != 0) {
            var tree = history.removeLast();
            switch(tree.history.type) {
                case JOIN:
                    add_graph(tree.history.bag_id + " (join) <- " + tree.history.p1.history.bag_id + " & " + tree.history.p2.history.bag_id, tree);
                    history.addLast(tree.history.p1);
                    history.addLast(tree.history.p2);
                    break;
                case LEAF:
                    add_graph(tree.history.bag_id + " (leaf)", tree);
                    break;
                case FORGET:
                    add_graph(tree.history.bag_id + " (forget) <- " + tree.history.p1.history.bag_id , tree);
                    history.addLast(tree.history.p1);
                    break;
                default:
                    add_graph(tree.history.bag_id + " (insert) <- " + tree.history.p1.history.bag_id , tree);
                    history.addLast(tree.history.p1);
            }
        }
    }

    private static class TreePanel extends JPanel {
        RootedTree tree;
        List<List<Integer>> nodes_by_level;
        Map<Integer, Integer> child_count;
        Set<Integer> dead_vertices;

        public TreePanel(RootedTree tree) {
            this.tree = tree;
            nodes_by_level = new ArrayList<>();
            child_count = new HashMap<>();
            dead_vertices = new HashSet<>();
            Map<Integer, Set<Integer>> deleted_children = new HashMap<>();
            int current_level = 0;
            ArrayDeque<Integer> current_level_nodes = new ArrayDeque<>();
            current_level_nodes.add(-1);
            ArrayDeque<Integer> next_level_nodes = new ArrayDeque<>();
            while (current_level_nodes.size() != 0) {
                nodes_by_level.add(new ArrayList<>());
                for (int current : current_level_nodes) {
                    nodes_by_level.get(current_level).add(current);
                    if (!deleted_children.containsKey(current)) {
                        next_level_nodes.addAll(tree.alive_children.get(current));
                        child_count.put(current, tree.alive_children.get(current).size() + tree.removed_subtrees.get(current).size());
                        deleted_children.put(current, new HashSet<>());
                        for (var deleted_subtree : tree.removed_subtrees.get(current)) {
                            next_level_nodes.add(deleted_subtree.root);
                            dead_vertices.add(deleted_subtree.root);
                            var structure = deleted_subtree.generate_structure();
                            dead_vertices.addAll(structure.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
                            deleted_children.putAll(structure);
                            deleted_children.get(current).add(deleted_subtree.root);
                        }
                    } else {
                        next_level_nodes.addAll(deleted_children.get(current));
                        child_count.put(current, deleted_children.get(current).size());
                    }
                }
                current_level++;
                current_level_nodes = next_level_nodes;
                next_level_nodes = new ArrayDeque<>();
            }
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Stroke continuous = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
            Stroke dashed = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
            var g2d = (Graphics2D) g.create();
            g2d.setColor(Color.BLACK);
            g2d.drawString("" + this.tree.getKeyValue(), 50, 50);
            int width = this.getWidth();
            int height = this.getHeight();
            int current_y = 0;
            int child_y = height / (nodes_by_level.size() + 1);
            for (int level = 0; level < nodes_by_level.size(); ++level) {
                current_y = child_y;
                child_y += height / (nodes_by_level.size() + 1);
                int current_x = 0;
                int child_x = 0;
                int child_index = 0;
                for (int current : nodes_by_level.get(level)) {
                    current_x += width / (nodes_by_level.get(level).size() + 1);
                    for (int child = 0; child < child_count.get(current); child++) {
                        if (dead_vertices.contains(nodes_by_level.get(level + 1).get(child_index))) {
                            g2d.setColor(Color.LIGHT_GRAY);
                            g2d.setStroke(dashed);
                        } else {
                            g2d.setColor(Color.BLACK);
                            g2d.setStroke(continuous);
                        }
                        child_x += width / (nodes_by_level.get(level + 1).size() + 1);
                        g2d.drawLine(current_x, current_y, child_x, child_y);
                        child_index += 1;
                    }

                    g2d.setStroke(continuous);
                    g2d.setColor(Color.WHITE);
                    g2d.fillOval(current_x - 20, current_y - 20, 40, 40);
                    g2d.setColor(Color.BLACK);
                    g2d.drawString("" + (current), current_x - 5, current_y + 5);
                    if (tree.height_mapping.containsKey(current)) {
                        g2d.drawString("" + tree.height_mapping.get(current), current_x + 25, current_y-5);
                        g2d.drawString("" + tree.get_depth_map().get(current), current_x - 35, current_y - 5);
                    }
                    if (!tree.active_vertices.contains(current)){
                        g2d.setColor(Color.LIGHT_GRAY);
                        if (dead_vertices.contains(current)) {
                            g2d.setStroke(dashed);
                        }
                    }
                    g2d.drawOval(current_x - 20, current_y - 20, 40, 40);
                }
            }
            g2d.dispose();
        }
    }
    private static class Pair {
        RootedTree tree;
        int index;
        public Pair(RootedTree tree, int index) {
            this.tree = tree;
            this.index = index;
        }
    }
}
