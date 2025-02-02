package util;

import java.util.*;

public class RootedTreeSet implements Iterable<RootedTree> {
    public HashMap<Integer, Set<RootedTree>> buckets;

    private int size = 0;


    public RootedTreeSet() {
        buckets = new HashMap<>();
    }

    public RootedTreeSet(RootedTree single_element) {
        this();
        insert_tree(single_element);
    }

    public Set<RootedTree> get_compatible_trees(RootedTree tree) {
        var hash_value = tree.getKeyValue();
        return buckets.containsKey(hash_value) ? buckets.get(hash_value) : new HashSet<>();
    }

    public void insert_tree(RootedTree tree) {
        var hash_value = tree.getKeyValue();
        if (!buckets.containsKey(hash_value)) {
            buckets.put(hash_value, new HashSet<>());
        }
        buckets.get(hash_value).add(tree);
        size++;
    }

    public void remove_tree(RootedTree tree) {
        var hash_value = tree.getKeyValue();
        buckets.get(hash_value).remove(tree);
        size--;
        if (buckets.get(hash_value).isEmpty()) {
            buckets.remove(hash_value);
        }
    }

    public int size() {
        return size;
    }

    public void show_information() {
        System.out.printf("#Buckets: %d, biggest bucket contains %d partial decompositions.\n", buckets.keySet().size(), buckets.values().stream().map(Set::size).reduce(0, Math::max));
    }

    @Override
    public Iterator<RootedTree> iterator() {
        return new RootedTreeSetIterator(this);
    }


    private class RootedTreeSetIterator implements Iterator<RootedTree> {

        private RootedTreeSet iterate_over = null;

        private Iterator<RootedTree> current_list = null;

        private final Iterator<Integer> hash_values;

        public RootedTreeSetIterator(RootedTreeSet iterate_over) {
            this.iterate_over = iterate_over;
            this.hash_values = iterate_over.buckets.keySet().iterator();
            if (hash_values.hasNext()) {
                current_list = iterate_over.buckets.get(hash_values.next()).iterator();
            }
        }

        @Override
        public boolean hasNext() {
            return current_list != null && (current_list.hasNext() || hash_values.hasNext());
        }

        @Override
        public RootedTree next() {
            if (!current_list.hasNext())
                current_list = iterate_over.buckets.get(hash_values.next()).iterator();
            return current_list.next();

        }
    }

}
