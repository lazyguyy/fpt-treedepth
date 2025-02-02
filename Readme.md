This repository contains code for an fpt algorithm for treedepth. Besides the graph, it also requires a nice tree decomposition of the input graph (it doesn't need to be of optimal depth). To achieve a true fpt algorithm, you would need to find these with an fpt algorithm for treewidth. However, the focus of this work was on the treedepth part, so we used a heuristic to find nice tree decompositions.

To test it, run the Main.java with the following arguments: <graph file> <nice tree decomposition file>
or supply the graph, nice td directly via std.in

After the algorithm has finished, a file <graph file>.solution or tdd.solution (depending on which way the input was supplied) containing the solution will be created.
The format of all inputs and outputs will be as specified on the PACE website (the DIMACS format)
Notice that the algorithm runs for a very long time if the solution of the graph is complicated.

To generate some insight into the process of how the solution was found you may uncomment the two marked lines in the Main.java and set the DEBUG-variable in RootedTree.java to true.
