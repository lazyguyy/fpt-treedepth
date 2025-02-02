
import io.GraphReader;
import io.GraphWriter;
import solver.Solver;
import testing.GraphPainter;
import util.Graph;
import util.TreeDecomposition;

import java.io.IOException;
import java.util.Scanner;


public class Main {
    public static void main(String[] args) {
        TreeDecomposition tree_decomposition;
        Graph input_graph;

        try {
            String id;
            String solution_name = "";
            if (args.length >= 1) {
                id = args[0];
                solution_name = args[1];
            } else {
                System.out.println("Enter id");
                Scanner s = new Scanner(System.in);
                id = s.next();
            }
            String path = "./instances/exact_";
            input_graph = GraphReader.read_graph(path+id+".gr");
            tree_decomposition = GraphReader.read_tree_decomposition(path+id+".ntd");
            int maxdepth = tree_decomposition.width;
            boolean solved = false;
            while (!solved) {
                System.out.printf("[%s] Attempting to solve, max depth is %d.\n", id, maxdepth);
                Solver solver = new Solver(input_graph, tree_decomposition, maxdepth);
                solver.solve();
                solved = solver.results.size() > 0;
                String name = "./solutions/" + solution_name + (args.length > 0 ? args[0] + ".solution" : "tdd.solution");
                if (!solved) {
                    System.out.printf("[%s] No solution with height less than or equal to %d was found.\n", id, maxdepth);
                } else {
                    System.out.printf("[%s] Solution with height %d was found.\n", id, maxdepth);
                    var best_solution = solver.get_minimal_depth_solution();
                    if (solver.verify_solution(best_solution)) {
                        GraphWriter.save_tree(best_solution, name);
                    }
                    return;
                }
                maxdepth++;
            }
            // To view solution, uncomment the following two lines
            // Set DEBUG in RootedTree to true to additionally see how the solution was created.
//            GraphPainter painter = new GraphPainter();
//            painter.add_graph_with_history("solution", best_solution);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
