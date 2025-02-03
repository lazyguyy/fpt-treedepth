This repository contains code for an fpt algorithm for treedepth. Besides the graph, it also requires a nice tree decomposition of the input graph (it doesn't need to be of optimal depth). To achieve a true fpt algorithm, you would need to find these with an fpt algorithm for treewidth. However, the focus of this work was on the treedepth part, so we used a heuristic to find nice tree decompositions.

The entry point is in ```Main.java```. It takes two arguments, the ```instance name``` as well as an ```output_file``` name. Once the algorithm is finished, the resulting treedepth decomposition will be written to that file. You can also supply both arguments via ```stdin```. For example, if the compiled program is in ```fpt_treedepth.jar``` and the instance you would like to test is in ```./instances/instance.gr```, you can call the program with ```java -jar fpt_treedepth.jar ./instances/instance ./solutions/solution.tdd```. The algorithm assumes that a file ```./instances/instance.ntd``` containing a ntd for the given instance exists. The gr and ntd file should be in the format specified here: https://pacechallenge.org/2020/td/#appendix-a-input-format-for-both-tracks and the output file will also adhere to the format specified on that page.

To generate some insight into the process of how the solution was found you may uncomment the two marked lines in the Main.java and set the DEBUG-variable in ```solver/Solver.java``` to true.

Currently, the ntd file for the instance exact_001.gr is broken
