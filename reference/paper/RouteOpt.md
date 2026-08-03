# RouteOpt: An Open-Source Modular Exact Solver for Vehicle Routing Problems

Zhengzhong You,<sup>a,b</sup> Yu Yang<sup>a,b,</sup>* 

<sup>a</sup> Department of Industrial and Systems Engineering, University of Florida, Gainesville, Florida 32611; <sup>b</sup> Center for Applied Optimization, University of Florida, Gainesville, Florida 32611 

Contact: you.z@ufl.edu, https://orcid.org/0009-0007-3480-5578 (ZY); yu.yang@ise.ufl.edu, https://orcid.org/0000-0002-0502-7603 (YY) 

Received: June 20, 2025 Revised: January 17, 2026; March 11, 2026 Accepted: April 4, 2026 Published Online in Articles in Advance: May 21, 2026 

https://doi.org/10.1287/ijoc.2025.1415 

Copyright: © 2026 INFORMS 

Abstract. Despite significant advancements in exact methods for vehicle routing problems (VRPs) over the past three decades, there remains a lack of high-performing and accessible open-source solvers for researchers and practitioners. To bridge this gap, we introduce RouteOpt, the first open-source modular exact solver for VRPs, delivering state-of-the-art performance while maintaining a flexible and extensible structure. RouteOpt achieves the best performance reported in the literature on both the capacitated vehicle routing problem (CVRP) and the VRP with time windows. Crucially, its modular design allows users to develop and integrate customized branching, cutting-plane, and variable reduction modules to tackle a broad range of VRP variants. Furthermore, RouteOpt introduces a novel node restoration mechanism, enabling efficient parallel processing of a branch-and-bound tree. Leveraging this feature, we have for the first time proven the optimality of three open CVRP instances. By combining modularity, efficiency, and open accessibility, RouteOpt establishes itself as an invaluable platform for both academic research and real-world applications. The source code of RouteOpt and relevant data are publicly available in the RouteOpt repository. 

History: Accepted by Ted Ralphs, Area Editor for Software Tools. 

Funding: This work was supported by the Division of Civil, Mechanical and Manufacturing Innovation [Grant CMMI-2309667]. 

Supplemental Material: The software that supports the findings of this study is available within the paper and its Supplemental Information (https://pubsonline.informs.org/doi/suppl/10.1287/ijoc.2025.1415) as well as from the IJOC GitHub software repository (https://github.com/INFORMSJoC/2025.1415). The complete IJOC Software and Data Repository is available at https://informsjoc.github.io/. 

Keywords: vehicle routing • open-source exact solver • modular design 

## 1. Introduction

The branch-price-and-cut (BPC) solution framework is widely recognized as the most effective method for solving vehicle routing problems (VRPs). It typically provides tighter bounds and proves optimality faster than alternative branch-and-cut-based approaches. Despite the proven effectiveness of BPC algorithms, their implementation remains notoriously complex. As noted by Pessoa et al. (2021), designing and coding a sophisticated BPC system can require several months of effort from a skilled team. This complexity often demands deep expertise in both algorithm design and operations research, making BPC-based solvers inaccessible to many research ers and practitioners who could benefit from their use. 

To address this critical gap, RouteOpt has been developed as an open-source, modular exact solver that implements a BPC framework alongside state-of-the-art optimization techniques. Specifically, it integrates advanced branching strategies, including three-phased branching (3PB) (Pessoa et al. 2020) and two-stage learning-based branching (2LBB) (You et al. 2026), along with their corresponding dynamic versions, effectively balancing branching variable selection efficiency and quality. For pricing (i.e., column generation), RouteOpt implements bidirectional labeling (Righini and Salani 2006), ng-route relaxation (Baldacci et al. 2011), decremen tal state space relaxation (Martinelli et al. 2014), dynamic ng augmentation (Roberti and Mingozzi 2014, Bulhoes et al. 2018a), bucket graphs (Sadykov et al. 2021), reduced-cost (RC) fixing-based arc elimination (Irnich et al. 2010, Sadykov et al. 2021), heuristic pricing (Pessoa et al. 2020), and dual stabilization (Pessoa et al. 2018). Regard ing cutting planes, it employs the well-established rounded capacity cuts (RCCs) (Naddef and Rinaldi 2002, Lys gaard et al. 2004) and limited-memory rank 1 cuts (R1Cs) (Pecin et al. 2017b, Bulhoes et al. 2018b), further boosting efficiency in proving optimality. Additionally, RouteOpt utilizes an enumeration procedure (Baldacc et al. 2008, Sadykov et al. 2021, Yang 2023) and the novel variable reduction method, deep Lagrangian underesti mate fixing (DeLuxing), from Yang (2025) to close a branch-and-bound node (BBN) when the optimality gap is small enough, further accelerating the solution process. 

Equipped with these advanced techniques, RouteOpt achieves a remarkable breakthrough by proving the optimality of capacitated vehicle routing problem (CVRP) instances that would require years to solve with other exact VRP solvers. This demonstrates RouteOpt’s exceptional capability to efficiently tackle large-scale VRPs. 

## 1.1. Contributions

RouteOpt is designed with a modular architecture and an open-source philosophy, offering accessibility and adaptability to both researchers and practitioners. It allows users to easily customize or extend individual components to meet their specific research or practical needs. Notable high-performance modules, each implemented as separate, dedicated classes for maximum flexibility and clarity, include (i) a branching module for efficien branching selection and management of the branch-and-bound tree (BBT), (ii) two specialized cutting-plane modules (R1Cs) and RCCs) for effective cut separation and streamlined RC computations in the labeling algo rithm (for column generation) and constraint matrix updates, and (iii) a robust variable reduction module leveraging the DeLuxing approach proposed in Yang (2025). Furthermore, RouteOpt introduces a novel node restoration functionality, allowing critical node information, including solver environments and settings, to b externally saved and restored. This capability enables parallel computation across machines to achieve massive scalability. 

This work makes the following key contributions. 

1. RouteOpt empowers researchers to test novel approaches by seamlessly replacing individual modules, avoid ing the need to develop an entire solver from scratch. For example, branching strategy researchers, such as those studying the cluster branching method from Silva et al. (2024), only need to specify the branching variable type and candidate set based on the current linear program (LP) fractional solution. RouteOpt can then employ its default 3PB or best-k formula (BKF) (You et al. 2026) or any user-defined selection strategies without code modification, thereby streamlining both experimental setup and evaluation. Similarly, researchers exploring new cutting plane separation methods, such as neural network-based RCC separators (Kim et al. 2024), can assess integrative performance within a state-of-the-art BPC framework, facilitating direct comparison with established tools, like CVRPSEP (Lysgaard 2003). As a result, RouteOpt dramatically lowers the barrier for related research, including the largely unexplored learning-based R1C separation, creating new opportunities for advancing algorithmic and learning-to-optimize research in exact VRP solution methods. 

2. RouteOpt enables the rapid and straightforward development of exact solvers for a wide range of VRP variants. Many variants can be prototyped by customizing the pricing routine that generates feasible routes under variant-specific resources while reusing existing modules as is. Importantly, RouteOpt is not limited to pricing-only extensions; for variants requiring additional master-problem constraints beyond standard set partitioning (e.g., route-level side constraints, such as fleet, shift, or budget constraints), users can augment the route-based maste with extra linear constraints defined on route attributes and incorporate the corresponding dual terms into reduced costs during pricing. This design facilitates research on problems such as the capacitated multitrip vehicle routing problem with time windows (Yang 2023), the heterogeneous fleet vehicle routing problem (Sadykov et al. 2021), and the split-delivery vehicle routing problem (SDVRP) (Balster et al. 2023), and it supports methodological inno vations in pricing, such as machine learning-based column selection (Morabit et al. 2021), by supporting compre hensive evaluation beyond the root node. 

3. RouteOpt introduces a unique node restoration feature that allows researchers to save and reload solver states and related data. This capability enables distributed computing for tackling challenging, large-scale instances. Furthermore, it supports rigorous benchmarking of heuristic methods and enables in-depth analysis of optimal solu tion structures, unlocking new potential for research on large-scale VRP instances. 

The remainder of the paper is organized as follows. Section 2 reviews related solvers, highlighting their distinctive features and contributions to VRP research. Section 3 describes the workflow of RouteOpt, providing insights into its operational logic and process flow. Sections 4–7 detail the core modules, summarizing the underlying algorithms and providing usage examples for each. Section 8 presents computational results that demonstrate the efficiency and effectiveness of RouteOpt in addressing two fundamental VRPs: the CVRP and vehicle routing problem with time windows (VRPTW). Finally, Section 9 concludes with a discussion on the impact of open-source, modular solvers, like RouteOpt, in advancing vehicle routing and other combinatorial optimization challenges and outlines directions for future research. 

## 1.2. Licensing and Solver Dependencies

RouteOpt is released under the GNU General Public License v3. It interfaces with external commercial mixed-intege program (MIP) solvers (e.g., IBM ILOG CPLEX and Gurobi) through a solver-adapter layer. These solvers are not distributed with RouteOpt and must be obtained separately under their respective licenses. Users are responsible for ensuring that their use of any third-party solver complies with the vendor’s terms. The adapter-based architecture makes it straightforward to add or substitute solver back ends without modifying the core RouteOpt code. 

## 2. Comparative Analysis of Existing VRP Solvers

In this section, we review the most relevant solvers to RouteOpt. Although a comprehensive survey is beyond our scope, we focus on three (two heuristic and one exact) notable solvers selected for their state-of-the-art performance, user-friendly design, or both. For a broader overview of related solvers, we refer readers to Wouda et al. (2024). We summarize the key strengths, limitations, and ideal use cases of each solver; clarify how RouteOpt distinguishes itself within this landscape; and guide researchers in choosing the tool that best aligns with their specific needs. 

Vidal (2022) introduces hybrid genetic search (HGS)-CVRP, a high-performance open-source hybrid genetic search solver designed specifically for the CVRP. Building on the foundation of Vidal et al. (2012), it incorporates more than a decade of methodological refinements, including the SWAP* operator, which significantly enhances local search performance. HGS-CVRP remains among the most competitive metaheuristics in terms of solution quality. It is implemented in C++ with wrappers available in C, Python, and Julia. Owing to its exceptional per formance and focused design, it is ideal for quickly obtaining high-quality solutions for CVRP instances or serving as a primal heuristic within exact solvers. It is worth mentioning that RouteOpt leverages HGS-CVRP to obtain a primal solution when a good one is not provided by users. However, despite being open source, adapting HGS-CVRP to other VRP variants requires considerable effort and domain expertise. Therefore, for use cases that require customization, a more versatile solver may be preferable. 

Wouda et al. (2024) presents PyVRP, a Python package implementing a variant of the HGS algorithm origi nally introduced in Vidal et al. (2013) for the VRPTW. PyVRP has steadily expanded its capabilities and now supports over six core VRP variants while maintaining strong performance. It strikes a balance between flexibilit and efficiency by delegating performance-critical components to C++ while retaining all user customizations at the Python level. The package is well documented and adheres to strong software engineering practices, making it highly extensible. This emphasis on generality comes at the cost of a slight decrease in solution quality, although the impact is often negligible for small- to medium-sized instances. Because exact solvers are often lim ited by time constraints to moderate-sized instances, PyVRP can serve as an effective primal heuristic. However, if the highest-possible solution quality is required, especially for large-scale instances, a specialized solver may be more appropriate. 

Lin et al. (2026) complements PyVRP by addressing the SDVRP, where a customer’s demand may be served by multiple vehicles. Lin et al. (2026) introduces Alkaid-SDVRP, an open-source C++ solver built on a multistart iter ated local search that integrates randomized variable neighborhood descent, reloading mechanisms, and a late acceptance criterion. It offers a configurable portfolio of neighborhoods that users can reorder or extend, and it is distributed both as a library and as a stand-alone executable with command-line and configuration-file interfaces, facilitating reproducible benchmarking. Alkaid-SDVRP won the SDVRP track of the 12th DIMACS Implementa tion Challenge. Although the solver is intentionally specialized for SDVRP, its operator design provides a strong baseline and effective primal heuristics for VRP variants that retain split deliveries as a central modeling feature. 

Pessoa et al. (2020) introduces VRPSolver, one of the leading BPC-based exact solvers for VRPs. Built upon BaPCod (Sadykov and Vanderbeck 2021), it supports multiple VRP variants through a flexible modeling inter face. Its Julia front end allows users to define decomposition schemes with reformulation automatically generated, eliminating the need to explicitly model the master program or pricing subproblems. VRPSolver also offers customization options, including cut generation and branching callbacks, while providing high-performing default column generation, cut separation, and branching variable selection for VRPs. A simplified Python inter face (Errami et al. 2024) further increases accessibility, allowing use by researchers with limited optimization expertise. Unlike traditional solvers, this interface abstracts away explicit integer programming (IP) formulations. However, its support is limited to standard VRP variants (e.g., those with capacity constraints, time windows and heterogeneous fleets), and the underlying implementation details of its sophisticated BPC method remain proprietary. 

As discussed, BPC-based solvers are notoriously complex to implement and customize, and currently, no state-of-the-art exact solver offers both full open-source access and ease of customization. This lack of openness hinders innovation as any methodological improvements require substantial effort. RouteOpt fills this gap by providing an open-source, modular, and extensible BPC-based solver. Users can integrate only the components that they need without requiring familiarity with the entire system. Researchers interested in specific mecha nisms $( \mathrm { e . g . }$ ., pricing) can directly inspect and experiment with the relevant source code. This transparency and modularity lower the barrier to entry, promote reproducibility, and accelerate research on exact methods for VRPs and related combinatorial optimization problems. 

## 3. Overview

This section briefly reviews the VRP and BPC solution framework, and then, it summarizes RouteOpt’s work flow and software architecture to motivate the modular design in Sections 4–7. Figure 1 presents the core BPC loop and the interactions among column generation, cutting planes, branching, arc elimination, enumeration, and DeLuxing. Figure 2 complements this algorithmic view by outlining the solver-interface and controller archi tecture that separates solver adaptation, search control, and problem-specific components. Finally, Figure 3 illus trates the node restoration workflow, where node states are serialized and reloaded to initialize independen jobs, enabling scalable parallelization across machines for challenging large-scale instances. 

## 3.1. Preliminaries: VRPs and the BPC Solution Framework

A VRP is defined on a customer-based graph $G = \left( N , A \right)$ or $G = ( N , E )$ in the symmetric case, where the node set $N = D \cup V$ comprises the set of depots D (with $D = \{ 0 \}$ in the classical single-depot setting) and the set of customers V. Each arc $( i , j ) \in A$ has routing cost $c _ { i j }$ and possibly, a resource-consumption vector $\pmb { \tau } _ { i j } \in \mathbb { R } _ { \geq 0 } ^ { p }$ (e.g., travel time $t _ { i j }$ and distance $d _ { i j } )$ . Each customer $i \in V$ has a demand/resource vector $q _ { i } \in \mathbb { R } _ { \geq 0 } ^ { p }$ and optional attributes, such as a service duration s and a time window $[ e _ { i } , l _ { i } ]$ . A fleet K of vehicles is given, where each vehicle $k \in K$ has origin $o _ { k } \in D ,$ destination $d _ { k } \in D \left( o _ { k } = d _ { k } \right.$ in closed-route settings, possibly distinct in open or multidepot settings), capacity/resource bounds $Q _ { k } \in \mathbb { R } _ { \geq 0 } ^ { p } ,$ , and optional fixed operating costs or compatibility constraints. A solution is a set of feasible routes that serve customers according to the variant (e.g., exactly once, at most once, or with splits) while satisfying resource and variant-specific constraints. The objective typically minimizes total travel cost, possibly with penalties for soft constraints. 

The BPC solution framework employs a set-partitioning-style master problem over the set of feasible routes ${ \mathcal { R } } ,$ where each route $r \in \mathcal { R }$ corresponds to a column with decision variable $\lambda _ { r } ,$ cost $c _ { r } ,$ and incidence $a _ { i r } = 1$ if 


Figure 1. (Color online) The General Workflow of RouteOpt


![](images/b05142f1ee992255cb131c3f2a2f09c3332d50d9893b1dbc317f0b669c4b7169.jpg)



Figure 2. (Color online) Class Diagram of the CVRP/VRPTW Solver Interface and Controllers in RouteOpt


![](images/501488c9f54bc1f0e8d7d15b4c9396506ba00807f2a06f030c455f5fb00db1b3.jpg)



Note. I/O, input/output; UB, upper bound.



Figure 3. The Node Restoration Workflow in RouteOpt


![](images/4b703f5d12040a017a552aa14be30629777534812d92e142613a8dff7d7dab5e.jpg)


customer i is served by r: 

$$
\min \sum_ {r \in \mathcal {R}} c _ {r} \lambda_ {r} \quad \text { s.t. } \sum_ {r \in \mathcal {R}} a _ {i r} \lambda_ {r} = 1 \quad \forall i \in V, \quad \lambda_ {r} \in \{0, 1 \}.
$$

Its LP relaxation is obtained by relaxing $\lambda _ { r } \in \{ 0 , 1 \}$ into $\lambda _ { r } \geq 0$ . BPC embeds column generation within branch and cut; at each node, it repeatedly solves the restricted master problem (RMP), invokes pricing to generate routes with negative reduced costs, and adds cutting planes to strengthen the relaxation; branching is typically implemented by adding constraints, and nodes are pruned whenever their LP bound is not better than the incumbent upper bound (UB). Further details appear in Section 4.1. 

## 3.2. Core Operational Workflow

As shown in Figure 1, the RouteOpt workflow begins with the construction of the root node from scratch, whose state is initialized to zero. This indicates that the node is not yet enumerated, meaning that its column (variable) set has not been fully generated (see Baldacci et al. 2008 for details of the enumeration method). This root node is then inserted into the BBT to initiate the solution process. At each iteration, a node is selected from the BBT and processed by solving the RMP via column generation (i.e., repeated alternation between solving the RMP and invoking the pricing routine to generate beneficial columns). If the node is in an enumerated state (state 1), cut ting planes are immediately applied to improve the lower bound (LB). Otherwise, the solver attempts arc elimi nation and column enumeration. If successful, the node transitions to state 1; if not, it remains at zero. Cutting planes are then applied unless either the tailing-off condition is met (i.e., no significant improvement in the lower bound is observed) or the pricing process becomes prohibitively slow. If the LP solution remains fractional after the cutting phase, branching is typically performed. However, if the node is already in an enumerated state, Rou teOpt can alternatively apply DeLuxing, a procedure that reduces the size of the enumerated column pool, thereby decreasing the reliance on branching. Child nodes generated by branching inherit their parent’s state and are added to the BBT. This iterative process continues until the BBT is empty, marking the termination of the algorithm or when optimality has been certified. 

## 3.3. Solver Interface and Class Architecture

As illustrated in Figure 2, the solver interface adopts an adapter-centric design. The Solver class encapsulates external LP/MIP back ends (Gurobi (Gurobi Optimization LLC 2023), CPLEX (IBM Corporation 2023), and MindOpt (Alibaba DAMO Academy Decision Intelligence Laboratory 2026)) and exposes a unified interface fo model construction, reoptimization, bound management, and solution queries. The application-level driver CVRPSolver (and its VRPTW subclass) owns this adapter together with controllers for pricing (i.e., labeling algorithm), column generation loop, cutting, and learning, thereby decoupling problem logic from solver-specific implementations. 

The top-level tree search is handled by BBTController<Node, BrCType, Hasher>, which maintains an ordered collection of BbNode instances and relies on callbacks provided by CVRPSolver for pricing, cutting, branching tests, machine learning (ML)-based candidate selection, and node input/output (I/O). The BKFController adjusts phase-dependent branching effort via BKF, whereas BranchingTesting evaluates branch candidates by computing branching scores, including LP-based, heuristic-based, and exact scores, before for warding selected candidates to the next phase. Each BbNode encapsulates the RMP (Solver), route columns, cut pools (including RCC, R1C, and branching constraints), bucket arcs, and enumeration matrices (if any), and it supports cloning to generate child nodes after a branching decision. Pricing and column addition are imple mented by CVRP_Pricing and CVRP_AddColumnController, respectively; the former executes a labeling algorithm, and the latter adds newly generated columns to the RMP. Cut management is modularized into Rank1SeparationController, Rank1CoefficientGetter, and Rank1RCController, thereby decou pling cut separation, coefficient construction, and reduced-cost evaluation. Data ingestion (CVRP_ReadData-Controller), initialization (InitialController), and optional primal heuristics (HGSController) configure model parameters and initial incumbents, whereas TwoStageController implements deterministic node I/O in RouteOpt via a two-stage serialization protocol for node files. In stage 1, it reads a node file, unpacks and parses its headers and metadata, infers the required in-memory layout, and preallocates the buffers needed to reconstruct the node efficiently. In stage 2, it writes a node file by packing all solver-relevant information and serializing it with bit-level packing to reduce the disk footprint. Finally, learning-to-branch components (Learning2Branch, Predict, and GetTrainingData) provide optional ML-guided branching without altering the core search flow, preserving a clean separation among problem orchestration, search control, and solver adaptation. 

## 3.4. Scalable Parallelization via Node Restoration

The latest release of RouteOpt (version 2.0.0) introduces a powerful node restoration capability, enabling key node-specific information, such as solution environments and configuration settings, to be saved externally and subsequently, reloaded to fully reconstruct the solver’s state. This feature makes it possible to solve a single prob lem instance across machines simultaneously, potentially yielding orders-of-magnitude speedups for extremely challenging instances. 

The node restoration workflow, illustrated in Figure 3, demonstrates how this feature achieves the desired par allelism. Starting from the construction of the root node, the workflow proceeds into the branch-and-bound node Oracle. At each branching step, only one node is selected and passed to the BBT, whereas all other child nodes (possibly more than one for multiway branching) are saved externally. These external nodes are monitored by a separate program, typically implemented in Python or Bash scripts. When this external program detects a new external node file, it initiates a new job by loading the node data and reconstructing the corresponding BBN. Sim ilar to the root node process, every branching operation within these new jobs maintains only one single active node, saving the rest externally. The scalability of this workflow depends mainly on available computationa resources (i.e., CPUs and RAM). Ideally, running k jobs in parallel can yield up to a k-fold acceleration, provided there are no resource conflicts. When a job finds a better incumbent, it atomically updates a shared .ub file with the new global upper bound. Other jobs periodically poll this file to retrieve the latest bound and prune domi nated nodes. Although this example illustrates retaining only one active node, RouteOpt does not impose strict rules for node saving; users have full flexibility in deciding when or whether to save nodes externally. In practice, RouteOpt avoids saving nodes with fewer than 10,000 enumerated columns as these nodes can be closed fast. 

## 4. The Branching Module

The branching module in RouteOpt offers a robust and customizable framework for building exact BPC-based VRP solvers. It enables users to construct solvers by simply providing key callback functions (e.g., pricing and cutting functors) without having to implement the entire branching component from scratch. By default, the module offers an efficient implementation of the 3PB rule (Pessoa et al. 2020), which is a hierarchical branching strategy that progressively filters candidates via pseudocost and fractionality screening, LP-based testing, and finally, Chva´tal–Gomory (CG)-based testing, thereby concentrating expensive evaluations on a small subset of promising candidates and balancing computational effort with decision quality. Additionally, the module fea tures a dynamic adjustment mechanism (the BKF method from You et al. 2026), which adaptively determines the number of tests to perform before finalizing each branching decision. RouteOpt employs a best-bound-firs search strategy by default, but also, it supports user-defined node selection criteria. Beyond branching, the mod ule efficiently manages the BBT by allowing node sorting with custom rules, updating the global lower bound tracking the number of explored nodes, and maintaining detailed historical data. This comprehensive tracking supports intelligent solver control, such as detecting the tailing off of cutting planes, further enhancing solver performance. 

## 4.1. Algorithmic Structure and Branching Workflow

RouteOpt follows the classic BPC paradigm, which augments branch and bound with systematic integration of column generation and cutting planes. The algorithm begins by either restoring a previously saved node or constructing a fresh root node and inserting it into the BBT. At each iteration, a node is selected for processing, pricing is executed to obtain a valid local lower bound, historical branching information is collected, and cutting planes are applied to further tighten the LB. If the node is not terminated (i.e., its local LB remains below the current global upper bound), the algorithm performs branching. The branching rule may be user specified or default to the 3PB scheme; when BKF is enabled, the number of tests used to support this decision is selected adaptively. After imposing the resulting branching constraint, child nodes are generated and han dled according to the configuration; with writeOut enabled, selected children are written to external files with unique identifiers and removed from memory, whereas otherwise, all children are inserted back into the BBT. The BBT is then updated, and the process repeats until the tree is exhausted or optimality has been certified. 

## 4.2. Branching Module Configuration and Execution

Figure 4 provides a minimal usage sketch for configuring and executing the branching module on a CVRP instance. The workflow initializes the instance; constructs the root node; and defines all problem-specific callbacks for LP testing, column generation, pricing, cutting, and branching. It then instantiates the BBT controller by supplying essential problem data (e.g., the instance dimension and the incumbent upper bound), perphase evaluation budgets, optional BKF parameters, and auxiliary hooks for candidate selection and node I/O. During the tree search, the controller manages node processing, applies the 3PB selection rule, and invokes pricing and cut separation routines to tighten bounds and guide the search toward optimality. The same configuration pattern extends directly to other VRP variants (e.g., the VRPTW) by replacing the callback implementations. A complete, compliable example and detailed documentation of all parameters are available in the online RouteOpt user manual. 


Figure 4. (Color online) Minimal Usage Sketch of the Branching Module in RouteOp


```txt
int main(int argc, char** argv) {
    CVRPSolver cvrp(argc, argv); BbNode root;
    auto lp_test=[&](...) {return cvrp.processLPTesting(...); };
    auto cg=[&](...) {return cvrp.processCGTesting</*enum?*/>(...); };
    auto pricing=[&](...) {return cvrp.callPricingAtBeg(...); };
    auto cutting=[&](...) {return cvrp.callCutting(...); };
    auto branch=[&](...) {return cvrp.imposeBranching(...); };
    BBTController<BbNode, pair<int, int>, PairHasher> bbt(
    cvrp.getDim(), cvrp.refUB(),
    phase0, phase1, phase2, phase3, bkf_config, /* node API */ BbNode: : . . .
    lp_test, cg, pricing, cutting, branch,
    ml_candidate_selection, node_out_func, node_in_func);
    bbt.solve(&root, TIME_LIMIT);
    cvrp.printOptSol(cout, bbt.getNumNodesExplored(), bbt.getLowerBound());
} 
```

## 4.3. Customization and Extension of Branching Strategies

Research on branching strategies primarily focuses on two key aspects: the type of variables to branch and the variable selection rule. RouteOpt is designed to facilitate easy comparison and experimentation with methods along both dimensions. To customize the branching variable type, users need to provide a data structure that uniquely encodes each branching object. For edge branching, an edge (i, j) may be represented as std::pair<int,int> with values {i,j}. For cut set, branching is performed on the value $\begin{array} { r } { \omega _ { S } : = \frac { 1 } { 2 } \sum _ { e \in \delta ( S ) } x _ { e } , } \end{array}$ where $S \subseteq V$ is a customer subset, δ(S) is the (undirected) cut set induced by S, and $x _ { e }$ are edge-usage variables. Because ω is only determined by $S ,$ a container, such as std::vector<int> (storing the customer indices in S), suffices to represent the branching object. In addition, a corresponding hash function (e.g., Pair-Hasher) must be implemented as RouteOpt relies on std::unordered_map for internal mapping. Proper hash function design is vital to minimize collisions. Once these components are specified, the BBTController can be initialized as BBTController</* node_type*/, /* cut_type*/, /* cut_map*/>. 

For customizing the variable selection rule, users can override the default 3PB rule by providing their own selection function. For instance, to implement the 2LBB rule developed in You et al. (2026), users can pass a custom function, such as ml_candidate_selection, to the BBTController. This user-defined function will replace the default, and it is supplied with relevant historical data at each call, enabling more context-aware branching decisions. 

It is worth noting that although the current version of BBT supports multiway branching (allowing nodes to have more than two children), the default historical data collector currently only supports binary branching and stores information for left and right branches. To extend historical data tracking to multiway branching, the collector’s data structure must be modified accordingly. As an alternative, users may choose to disable historical data collection altogether, thereby avoiding any potential inconsistencies. 

## 5. The Limited-Memory Chva´tal–Gomory Rank 1 Cut Module

Chva´tal–Gomory cuts are a foundational class of cutting planes in integer programming that was first introduced by Chva´tal (1973). They are derived by taking nonnegative linear combinations of valid inequalities and round ing down the resulting coefficients. CG cuts, particularly their rank 1 variants, generated by a single round of aggregation and rounding are particularly effective in strengthening the LP relaxation and are widely used in modern IP solvers. 


Table 1. Optimal Multipliers for R1Cs (Bulhoes et al. 2018b)


<table><tr><td>Plan</td><td>Multipliers</td><td>Condition</td></tr><tr><td>0</td><td><eq>\left(\frac{1}{2},\frac{1}{2},\cdots,\frac{1}{2}\right)</eq></td><td>If n is odd</td></tr><tr><td>1</td><td><eq>\left(\frac{1}{3},\frac{1}{3},\cdots,\frac{1}{3}\right)</eq></td><td>If <eq>n = 3k + 2</eq> for <eq>k \geq 1</eq></td></tr><tr><td>2</td><td><eq>\left(\frac{n-3}{n-2},\frac{n-3}{n-2},\frac{2}{n-2},\frac{1}{n-2},\cdots,\frac{1}{n-2}\right)</eq></td><td><eq>n \geq 5</eq></td></tr><tr><td>3</td><td><eq>\left(\frac{n-2}{n-1},\frac{n-2}{n-1},\frac{2}{n-1},\frac{2}{n-1},\frac{1}{n-1},\cdots,\frac{1}{n-1}\right)</eq></td><td><eq>n \geq 5</eq></td></tr><tr><td>4</td><td><eq>\left(\frac{n-3}{n-1},\frac{2}{n-1},\frac{1}{n-1},\cdots,\frac{1}{n-1}\right)</eq></td><td><eq>n \geq 5</eq></td></tr><tr><td>5</td><td><eq>\left(\frac{n-2}{n-1},\frac{1}{n-1},\cdots,\frac{1}{n-1}\right)</eq></td><td><eq>n \geq 4</eq></td></tr><tr><td>6</td><td><eq>\left(\frac{n-2}{n},\frac{2}{n},\frac{2}{n},\frac{1}{n},\cdots,\frac{1}{n}\right)</eq></td><td><eq>n \geq 5</eq></td></tr></table>

The general form of an R1C applied to the set-partitioning formulation of VRPs is 

$$
\sum_ {r \in \mathcal {R}} \left\lfloor \sum_ {i \in C} p _ {i} a _ {i r} \right\rfloor \lambda_ {r} \leq \left\lfloor \sum_ {i \in C} p _ {i} \right\rfloor ,
$$

where R denotes the set of feasible routes, $C \subseteq V$ is the cut set, $p _ { i } \geq 0$ is the multiplier associated with customer $i \in C ,$ and $a _ { i r }$ indicates whether customer i is visited by route r. 

Each R1C is defined by the choice of customer subset C and a multiplier plan p, typically chosen from a set of theoretically optimal candidates. RouteOpt includes a list of such optimal multiplier plans as identified in Bul hoes et al. (2018b), which are summarized in Table 1. 

These cuts can substantially tighten the relaxation but may also increase the complexity of the pricing problem by expanding the labeling state space. To alleviate this burden, RouteOpt incorporates the limitedmemory technique from Pecin et al. (2017b), which is itself rooted in the ng-route relaxation introduced in Baldacci et al. (2011). Both mechanisms strategically relax the labeling state space, thereby simplifying dominance and accelerating the dynamic programming-based pricing routine without materially degrading bound quality. 

RouteOpt separates R1Cs through a staged pipeline that couples candidate generation, memory construction, and cut selection. First, it heuristically identifies violated R1C candidates using a procedure adapted from the local search strategy from Pecin et al. (2017a), which was designed to uncover highly violated cuts. For each retained candidate, RouteOpt constructs a corresponding memory set, with the search effort governed by the detected pricing hardness level (see Section 5.1). Finally, RouteOpt removes duplicates, selects a bounded num ber of cuts per iteration, and enforces memory size limits to control the added complexity induced in the pricing problem. 

## 5.1. Configuration and Execution of R1C Generation

Figure 5 illustrates the usage of the R1C separation module in RouteOpt. In this example, a Rank1Separation-Controller is first initialized, after which the process iteratively updates the controller and generates new rank 1 cuts. The workflow primarily relies on two key functions. 

• updateInfo. Updates the controller with the current LP solution and the set of previously generated cuts. 

```cpp
void updateInfo(MemoryType limited_memory_type,
    PRICING_HARD_LEVEL pricing_hard_level,
    bool if_collect_sol,
    const std::vector VirginInfo>& sol,
    const std::vector Virginc& old_cuts); 
```

Figure 5. (Color online) Example Usage of the Rank 1 Separation Module in RouteOpt 

```c
// prerequisites: sharedData, solver, cost_matrix, and parameters
Rank1CutsDataShared sharedData(dim);
Solver solver;
Rank1SeparationController sep(sharedData, max_row_rank1, max_r1c3_round, max_r1c_round, solver, cost_matrix);
std::vector<RouteInfo> routes = /* current solution routes */
std::vector<R1c> cut_pool = /* existing R1Cs */

sep.updateInfo(MemoryType::NODE_MEMORY, PRICING_HARD_LEVEL::EASY, true, routes, cut_pool);

std::vector<R1c> newCuts;
sep.separateRank1Cuts(newCuts, true, true);
sep.convert2ArcMemory(newCuts); // optional
/* add newCuts to the model and to cut_pool for the next iteration */ 
```

• separateRank1Cuts. Executes the rank 1 cut separation, optionally leveraging memory-based search and advanced cut selection strategies. 

```cpp
void separateRank1Cuts(std::vector<Rlc>& cuts,
    bool if_mem = true,
    bool if_select_cuts = true); 
```

These functions offer considerable flexibility through their input parameters. Users can specify the type of memory used (node based, arc based, or none), choose a pricing hardness level that controls how aggressively RouteOpt searches for memory sets during cut generation (using fast heuristics in easy mode, which may produce larger memory sets, and performing exact searches in hard mode), and decide whether to collect intermediate LP solutions. Collecting LP solutions is particularly advantageous when node-based memory becomes impractical because of pricing complexity as it enables a seamless transition to arc-based memory. Additionally, the separateRank1Cuts function provides two primary operational modes: memory-based search after cut separation and cut selection by balancing violation magnitude with memory set size. The lat ter is especially useful when pricing is computationally intensive and careful management of memory sets is critical. 

## 5.2. Customization and Integration of R1Cs

RouteOpt’s built-in R1C separation module is designed for ease of use, allowing users to generate cuts without knowledge of the internal implementation. However, for advanced research or customization, a deeper understanding of the internal structure and management of R1Cs is essential. Such insight enables seamless integration of custom separation strategies, efficient memory set identification, and effective dominance checks within label ing algorithms. 

The standard R1C representation in RouteOpt is defined as follows. 

```cpp
std::pair<std::vector<int>, int> info_r1c {}; 
```

In this representation, the first component stores an ordered list of customer indices, whereas the second com ponent indicates the specific multiplier plan employed. Although alternative formats may be employed in the initial stages of custom separation, integration with RouteOpt’s internal functions necessitates conversion to this standard representation. 

Figure 6 illustrates RouteOpt’s standard R1C separation procedure, encompassing both the cut generation and memory assignment. These operations are handled by the classes CutGenerator and MemGenerator, respectively. 

For customized separation methods, users only need to redefine the CutGenerator::generateRank1- Cuts() method with their own algorithms. Typically, such approaches require access to key data, including the 

Figure 6. (Color online) Implementation of the Rank 1 Separation in RouteOpt 

```cpp
void Rank1SeparationController::separateRank1Cuts(
    std::vector<R1c> &cuts, bool if_mem, bool if_select_cuts) {
    if (if_mem) memGen.fillMemory();
    cutGen.generateRank1Cuts();
    if (if_mem) memGen.findMemory4Cuts();
    if (if_select_cuts) cutSelector.selectR1CsByVioNMemory();
    setRhs(sharedData.refCuts());
    cuts = sharedData.refCuts();
    cleanData();
} 
```

current LP fractional solutions and route sequences. These details are readily available through member vari ables of the CutGenerator class. 

```cpp
std::reference_wrapper<const R1CsDataShared> r1CsDataShared_ref;
std::reference_wrapper<DataShared> sharedData_ref; 
```

To ensure smooth integration and streamlined data management, it is strongly recommended that any customized separation functions be implemented directly within the CutGenerator class. Similarly, users can define their own memory assignment procedures by customizing the MemGenerator::findMemory4Cuts() method. Again, necessary data structures are conveniently accessible via the provided references sharedData_ref and r1CsDataShared_ref. 

## 6. The Rounded Capacity Cut Module

RCCs are a class of cutting planes commonly used in capacity-constrained VRPs (e.g., the CVRP) to strengthen dual bounds derived from LP relaxations. Because RCCs are robust, meaning that they do not increase the com plexity of the pricing problem, RouteOpt separates and adds them first. Only when RCC separation tails off does RouteOpt proceed to nonrobust cuts (e.g., R1Cs), which may further tighten the bound but can also increase pric ing complexity. In its most straightforward form, an RCC is expressed as $x ( \overline { { S } } : S ) \geq 2 k ( S )$ , where $S \subseteq V$ is a cus tomer subset and ${ \overline { { S } } } = V \backslash S$ . Here, $x ( S , T )$ represents the undirected flow between customers in sets S and $T ,$ and k(S) indicates the minimum number of vehicles necessary to serve all customers in S. The factor 2 arises naturally because of the undirected nature of this flow representation. 

However, RCCs formulated in this way are typically dense and computationally expensive. To address this issue, the following sparse alternative forms (Forms 1 and 2) are typically employed: 

$$
\text {   Form   1:   } x (S: S) \leq | S | - k (S),
$$

$$
\text {   Form   2:   } x (\overline {{S}}: \overline {{S}}) + \frac {1}{2} x (\{0 \}: \overline {{S}}) - \frac {1}{2} x (\{0 \}: S) \leq | \overline {{S}} | - k (S).
$$

According to the guidelines provided in the CVRPSEP documentation (Lysgaard 2003), Form 1 is usually preferable when $\begin{array} { r } { | S | \le n / 2 . } \end{array}$ , whereas Form 2 is better suited for larger subsets. RouteOpt follows this recommendation by automatically selecting the sparsest formulation available. Furthermore, RouteOpt implements a strengthened variant (Baldacci et al. 2008), which is defined as 

$$
\text {   Form   3:   } \sum_ {r \in \mathcal {R}} I (| C (r) \cap S | \geq 1) \lambda_ {r} \geq k (S),
$$

where the indicator function $I ( | C ( r ) \cap S | \geq 1 )$ is equal to one if route r serves at least one customer in S and zero otherwise. Form $^ { 3 , }$ although nonrobust for labeling algorithms, is particularly useful in enumerated states where all necessary columns are explicitly known. 

RouteOpt’s RCC module is composed of three core components: 

• the CVRPSEP library (Lysgaard 2003), which automates RCC generation and selection; 

• efficient mapping of RCC dual values to reduced-cost updates in labeling algorithms; and 

• computation of the coefficients for RCCs in the RMP. 

Detailed descriptions of these components can again be found in the comprehensive RouteOpt documentation (You 2025). 

Figure 7. (Color online) Example Usage of the RCC Separation Module in RouteOpt 

```c
// prerequisites: dimension, capacity, demand, and current LP information
std::vector<double> x = /* fractional LP solution */;
std::vector<SequenceInfo> routes = /* routes extracted from current solution */;
std::vector<Rcc> rcc_pool = /* existing RCCs */

std::vector<Rcc> newRccs;
RCCSeparationController::generateRCCs(dimension, capacity, demand,
    /* keep_permanently = */ false,
    /* strengthened_form3 = */ true,
    x, routes, rcc_pool, newRccs);
/* add newRccs to the model and to rcc_pool for the next iteration */ 
```

## 6.1. Configuration and Execution of RCC Generation

RouteOpt leverages the CVRPSEP package for RCC separation, opting not to reimplement this functionality internally. RCC separation is managed via the RCCSeparationController class, primarily through its generateRCCs method. This method generates RCCs tailored to the current problem instance, utilizing parameters, such as problem size, customer demands, and the current LP solution. Its function signature is given below. 

```cpp
void generateRCCs(int dim,
    double cap,
    const std::vector<double>& demand,
    bool if_keep_rcc,
    bool if_strengthen_rcc,
    const std::vector<double>& sol_x,
    const std::vector<SequenceInfo>& sols,
    const std::vector<Rcc>& old_cuts,
    std::vector<Rcc>& new_cuts); 
```

The following two key Boolean parameters provide crucial flexibility for RCC generation. 

• if_keep_rcc. This specifies whether generated RCCs should remain active (permanent) throughout the entire solution process. This option is particularly valuable for VRPTW type 2 instances, where relaxing capacity constraints during pricing may yield integer solutions that violate these constraints. By retaining these RCCs permanently, RouteOpt effectively prevents repeated generation of the same infeasible solutions. 

• if_strengthen_rcc. Forces the use of strengthened RCCs, which should be enabled in enumerated states. 

Figure 7 demonstrates practical usage of the RCC separation module in RouteOpt. In this example, essential input parameters, such as the problem dimension, vehicle capacity, and customer demands, are initialized. The generateRCCs function is then called with key settings, controlling, for example, whether generated cuts should remain active throughout the LP solution process and whether strengthened RCCs are to be used. 

## 6.2. Customization and Integration of RCCs

As with R1Cs, users can customize RCC separation strategies within RouteOpt. For compatibility with RouteOpt’s RCC modules, custom implementations must adhere to the standard RCC data structure as summarized in Figure 8. 

In this structure, the vectors info_rcc_customer and info_rcc_outside_customer represent the cus tomer set S and its complement S, respectively. For RCCs in Form 1 and Form 3, where the complement set is not explicitly required, info_rcc_outside_customer may remain empty. To implement a custom RCC 

Figure 8. (Color online) Standard RCC Data Structure in RouteOpt separator, simply modify the body of RCCSeparationController::generateRCCs() as needed. Each new cut should specify the relevant fields in the standard structure, ensuring seamless integration with RouteOpt. 

```c
// RCC data structure
int form_rcc{}; // Specifies RCC formulation type
std::vector<int> info_rcc_customer{}; // Customer set S
std::vector<int> info_rcc_outside_customer{}; // Complement of set S
double rhs{}; // RHS value of the RCC
bool if_keep{}; // Flag indicating persistence across solution process 
```

## 7. The DeLuxing Module

Deep Lagrangian underestimate fixing, which was proposed in Yang (2025), is an effective variable-reduction method for exact column-generation-based algorithms. It repeatedly solves small auxiliary LPs over a carefully selected subset of enumerated columns to obtain informative dual vectors and Lagrangian underestimates that certify and remove unnecessary variables from the RMP. By relaxing the reliance on a single optimal dua solution—as in standard reduced-cost fixing—and instead, exploring multiple dual solutions with modest additional overhead, DeLuxing can eliminate up to 99% of variables and deliver substantial speedups in routing applications. In routing problems, DeLuxing is typically used in the following two ways 

• Column pool reduction. Rapidly pruning variables from fully enumerated pools and expediting the certifica tion of optimality for challenging instances. 

• Primal heuristics. Enumerating a manageable, high-quality set of columns by applying stringent cutoff criteria followed by DeLuxing to eliminate unnecessary variables. The resulting pool, typically containing 10,000–20,000 columns, is then sorted according to the reduced costs from the final DeLuxing iteration. The top-ranked columns can be efficiently provided to an IP solver, significantly improving the chances of rapidly finding high-quality feasi ble solutions. 

## 7.1. Configuration and Execution of Variable Reduction with DeLuxing

Figure 9 provides a practical demonstration of employing DeLuxing within RouteOpt. The example showcases how users can specify essential parameters to effectively control the variable reduction process. The key parameters influencing DeLuxing performance are as follows. 

```txt
int NClust = 20; // Number of clusters for grouping columns
int beta1 = 1000; // Initial heuristic threshold
int beta2 = 1000; // Deep-search heuristic threshold 
```

In particular, the parameter NClust determines the number of clusters into which variables are partitioned; increasing this value typically results in the removal of more variables but may require additional computationa time. The parameter beta1 dictates when to use the slower yet more accurate k-means++ clustering algorithm. If the column pool size is less than or equal to beta1, k-means++ is employed; otherwise, a faster, less precise heuristic method is used. Finally, beta2 controls the depth of the iterative variable elimination process; a smalle value enables more exhaustive exploration and potentially, greater variable reduction, albeit at the expense of increased run time. 

## 8. Computational Results

This section presents a comprehensive evaluation of RouteOpt’s performance on two classic VRPs: the CVRP and VRPTW. The goal is to demonstrate the solver’s efficiency through computational experiments conducted using well-established benchmark data sets. All code and data sets can be accessed in the RouteOpt repository (You and Yang 2026). 

## 8.1. Experimental Setup

The experimental evaluation uses two computing environments with distinct roles. Unless otherwise stated, all run-time measurements are conducted on four identical high-performance Dell workstations, each equipped with a 12th Gen Intel Core i9-12900K CPU at 3.2 GHz and 128 GB RAM and Ubuntu 20.04 LTS. The HiPerGator supercomputing cluster at the University of Florida is used only for large-scale parallel runs that require 

Figure 9. (Color online) Example Usage of DeLuxing in RouteOpt executing hundreds of jobs concurrently: in particular, the computations aimed at resolving previously open CVRP instances and establishing new optimality results (see Section 8.2). HiPerGator nodes run Red Hat Enterprise Linux 8.8 and provide two AMD EPYC 770264-core processors at 2.0 GHz and 1003 GB RAM per node; our allocation comprises 1080 CPU cores and 8640 GB RAM. All code was compiled using g++ version 9.4.0. 

```txt
// prerequisites: solver is built and an incumbent upper bound is available
Solver solver;
std::vector<int> Idxdel; // output: removed variable indices
DeLuxingController::deLuxing(solver, /*UB*/, /*NClust*/, /*beta1*/, /*beta2*/, 
    Idxdel, /*Timelimit*/, /*Tolerance*/, /*Verbose*/); 
```

## 8.2. Performance on CVRP Benchmarks

The CVRP involves routing a fleet of vehicles from a single depot to deliver goods to a set of customers, each exactly once while minimizing total travel distance and respecting vehicle capacity constraints. The foundational research on CVRP can be traced to the seminal work of Dantzig and Ramser (1959), which laid the groundwork for systematic studies in vehicle routing. Because of its pivotal role in transportation logistics and its academic significance, the CVRP has inspired numerous variants and remains a benchmark problem in operations research. Standard benchmark data sets for the CVRP, which are available via CVRPLIB (Lima et al. 2014) include sets A, B, E, F, M, P, and X. In this study, we consider instances with 60–199 customers categorized into two groups: small (60–99 customers) and medium (100–199 customers). 

We benchmark the computational performance of RouteOpt against VRPSolver v0.6.13 with BaPCod v0.82 and CPLEX 22.1.0 (IBM Corporation 2023) as the internal LP/IP solver. VRPSolver is a state-of-the-art exact CVRP solver developed by Pessoa et al. (2020). Both RouteOpt and VRPSolver are initialized with optimal solu tion values as UBs and a time limit of 3,600 seconds (one hour). Performance is assessed using the following metrics: optimality status, root node processing time in seconds, root node gap percentage, total computation time in seconds, number of explored nodes, final global gap percentage, and speedup computed as the ratio of VRPSolver time to RouteOpt time. Table 2 summarizes these metrics for both the small and medium groups, reporting the gap as an average and other metrics as geometric means. For the speedup metric, the geometric mean is computed only over instances solved by both solvers. Detailed numerical results are provided in Table A.1 in the appendix. 

The results demonstrate that RouteOpt consistently outperforms VRPSolver. For the small group, RouteOpt optimally solves all 22 instances, processing root nodes and proving optimality faster than VRPSolver. For the medium group, RouteOpt solves 27 of 30 instances optimally, whereas VRPSolver solves 25. Additionally, Rou teOpt achieves substantially lower overall CPU times (78.5 versus 125.1 seconds) and a smaller average final opti mality gap (0.02% versus 0.04%) at termination. 

## 8.3. Performance on VRPTW Benchmarks

The VRPTW extends the standard CVRP by adding an additional resource constraint related to time. Specifically, each customer is associated with a service time $s _ { i } \geq 0$ and earliest and latest service starting times $e _ { i } \geq 0$ and $l _ { i } \geq 0 _ { \AA }$ respectively, where $e _ { i } \leq l _ { i } .$ . Service for each customer must begin within this time window. Vehicles may wait if they arrive before $e _ { i }$ but must not arrive after $l _ { i } .$ The travel time from customer i to customer $j ,$ which is denoted as $t _ { i j } \geq 0 ,$ , is assumed equal to the corresponding distance $d _ { i j }$ in widely used benchmark instances. 

Our evaluation employs the Solomon classic benchmark data set (Solomon 1987), which consists of 56 instances with 100 customers each, and the HG-200 data set, comprising 60 instances with 200 customers. Bot data sets are publicly available from CVRPLIB (Lima et al. 2014). To ensure meaningful comparison, we excluded instances that both VRPSolver and RouteOpt solved in less than 60 seconds. The time limit is again set to 3,600 seconds. After filtering, the test set consisted of 8 Solomon instances and 50 HG-200 instances. For consistency, we report the same performance metrics as in the CVRP benchmarking. 

As summarized in Table 3, RouteOpt consistently outperforms VRPSolver on both data sets. For the Solomon set, RouteOpt solves all eight instances to optimality within the one-hour limit, whereas VRPSolver solves seven of eight instances. RouteOpt’s average solution time is 68.6 seconds per instance, which is less than half of 


Table 2. Summary of Performance on CVRP: RouteOpt vs. VRPSolver


<table><tr><td rowspan="2">Group</td><td colspan="6">RouteOpt</td><td colspan="6">VRPSolver</td><td rowspan="2">Speedup</td></tr><tr><td>Opt</td><td>Root/s</td><td>Root gap (%)</td><td>CPU/s</td><td>#Nodes</td><td>Gap (%)</td><td>Opt</td><td>Root/s</td><td>Root gap (%)</td><td>CPU/s</td><td>#Nodes</td><td>Gap (%)</td></tr><tr><td>Small</td><td>22/22</td><td>2.5</td><td>0.00</td><td>2.5</td><td>1.0</td><td>0.00</td><td>22/22</td><td>3.7</td><td>0.00</td><td>4.3</td><td>1.0</td><td>0.00</td><td>1.71</td></tr><tr><td>Medium</td><td>27/30</td><td>36.4</td><td>0.11</td><td>78.5</td><td>3.4</td><td>0.02</td><td>25/30</td><td>62.0</td><td>0.11</td><td>125.1</td><td>3.0</td><td>0.04</td><td>1.65</td></tr></table>


Note $\mathrm { C P U } / \mathrm { s } ,$ total CPU computation time in seconds; ${ \mathrm { G a p } } / { \% } ,$ final global gap percentage; #Nodes, number of explored nodes; Opt, optimality status (one for optimal and zero otherwise); Root gap/%, root node gap percentage; Root/s, root node processing time in seconds. 



Table 3. Summary of Performance on VRPTW: RouteOpt vs. VRPSolver


<table><tr><td rowspan="2">Group</td><td colspan="6">RouteOpt</td><td colspan="6">VRPSolver</td><td rowspan="2">Speedup</td></tr><tr><td>Opt</td><td>Root/s</td><td>Root gap (%)</td><td>CPU/s</td><td>#Nodes</td><td>Gap (%)</td><td>Opt</td><td>Root/s</td><td>Root gap (%)</td><td>CPU/s</td><td>#Nodes</td><td>Gap (%)</td></tr><tr><td>Solomon</td><td>8/8</td><td>57.6</td><td>0.02</td><td>68.6</td><td>1.3</td><td>0.00</td><td>7/8</td><td>143.5</td><td>0.05</td><td>167.7</td><td>1.3</td><td>0.03</td><td>2.60</td></tr><tr><td>HG</td><td>38/50</td><td>229.9</td><td>0.34</td><td>592.9</td><td>5.4</td><td>0.18</td><td>32/50</td><td>389.1</td><td>1.06</td><td>720.1</td><td>2.7</td><td>0.94</td><td>1.25</td></tr></table>


Note. CPU/s, total CPU computation time in seconds; Gap/%, final global gap percentage; #Nodes, number of explored nodes; Opt, optimality status (one for optimal and zero otherwise); Root gap/%, root node gap percentage; Root/s, root node processing time in seconds. 


VRPSolver’s 167.7 seconds, demonstrating a significant speed advantage. Moreover, RouteOpt achieves a better root node gap (0.02% compared with 0.05%) in less CPU time (57.6 versus 143.5 seconds). On the more challeng ing HG-200 data set, RouteOpt again demonstrates superior performance, solving 38 of 50 instances optimally compared with 32 of 50 instances for VRPSolver. RouteOpt achieves a remarkably low average optimality gap of just 0.18%, significantly outperforming VRPSolver’s 0.94%. Additionally, RouteOpt processes root nodes more quickly (229.9 versus 389.1 seconds), maintains a smaller root gap (0.34% versus 1.06%), and keeps total CPU time lower (592.9 versus 720.1 seconds), even though it explores more nodes per instance (5.4 compared with 2.7 for VRPSolver). Detailed numerical results are provided in Table A.2 in the appendix. 

RouteOpt outperforms VRPSolver in these two case studies primarily for two reasons. First, the two solvers emphasize different mechanisms for closing the optimality gap: RouteOpt follows a branching-focused strategy, whereas VRPSolver relies more heavily on cutting planes. In RouteOpt, the preference for closing the gap via branching is driven by 2LBB-dy (You et al. 2026), which provides high-quality branching decisions at low com putational cost. This makes it more advantageous for RouteOpt to invest in branching rather than execute exten sive rounds of cutting planes that yield diminishing improvements in the dual bound. Second, RouteOpt’s R1C separation constructs (near-)minimal memory sets and selectively adds cuts by jointly balancing expected bound improvement with memory-set size. This design controls the increase in pricing complexity and results in sub stantially faster column generation compared with VRPSolver. 

## 8.4. Solution of Open CVRP Instances

In this section, we aim to address the challenge of proving optimality for several previously unsolved instances using RouteOpt’s novel node restoration feature. Specifically, we target the well-known and challenging CVRP instances X-n313-k71, X-n359-k29, and X-n384-k52. Proving optimal solutions for these problem instances on a single machine would typically require an impractical amount of computational effort, even when using the best-known value as an upper bound. To ensure a clear analysis of any identified optimal solution, we initialize the upper bound u to the best-known solution value v<sup>∗</sup> plus one (i.e., u � v<sup>∗</sup> + 1). Table 4 presents our results, reporting key metrics, such as root node processing time, root node gap percentage, total computing time in CPU years, and total number of explored nodes aggregated from all solution logs. For the node yielding the opti mal solution, we further report its branching structure, specifying the total number of branches taken as well as the respective counts of left (edges forbidden) and right (edges required) branches from the root to this optimal solution. 

Notably, the root node gaps obtained are impressively small (ranging from 0.22% to 0.29%), with root node solution times of just a few hundred seconds. This clearly demonstrates the effectiveness of RouteOpt’s advanced cutting and reduced-cost fixing techniques in tightening dual bounds at the root. However, despite achieving such narrow root gaps rapidly, proving optimality with conventional methods remains computationally prohibitive, often requiring one or more CPU years. This challenge arises from the need to explore millions of branch-and-bound nodes, even when employing advanced branching strategies, such as 2LBB combined with BKF. Fortunately, RouteOpt’s node restoration capability enables a dramatic reduction in wall time when utilizing supercomputing resources, like the HiPerGator cluster, cutting the run time for each instance down to just a few days. 


Table 4. Performance on Open CVRP Instances


<table><tr><td>Instance</td><td>Root/s</td><td>Root gap/%</td><td>CPU/y</td><td>#Nodes/<eq>10^6</eq></td><td>#Total branches</td><td>#Left branches</td><td>#Right branches</td></tr><tr><td>X-n313-k71</td><td>149.66</td><td>0.22</td><td>0.49</td><td>0.71</td><td>33</td><td>17</td><td>16</td></tr><tr><td>X-n359-k29</td><td>926.34</td><td>0.24</td><td>0.36</td><td>0.04</td><td>17</td><td>9</td><td>8</td></tr><tr><td>X-n384-k52</td><td>575.01</td><td>0.29</td><td>1.50</td><td>0.62</td><td>26</td><td>16</td><td>10</td></tr></table>


Note. CPU/y, total computing time in CPU years; #Left branches, number of left branches; #Nodes/10<sup>6</sup>, total number of explored nodes; #Right branches, number of right branches; Root gap/%, root node gap percentage; Root/s, root node processing time in seconds; #Total branches, number of branches. 


Additionally, we observe a clear relationship between the route length, which is defined as the average num ber of customers visited per vehicle, and the number of nodes explored. Instances with shorter average route lengths tend to require the exploration of more BBNs. For example, instance X-n313-k71, with an average route length of approximately $3 1 3 / 7 1 \approx 4 . 4 ,$ , required exploring about 0.71 million nodes. In contrast, instance X-n384-k52, with an average route length of approximately $3 { \breve { 8 } } 4 / 5 2 \approx 7 . 4 ,$ , required fewer nodes (0.62 million), and instance X-n359-k29, with the longest average route length of approximately $3 5 9 / 2 9 \approx 1 2 . 4 ,$ , required just 0.04 million nodes. An additional noteworthy pattern is observed in the branching structure; across all three instances, more left branches are consistently needed to reach optimality. Recognizing this branching tendency could offer valuable strategic insights for expediting the search for optimal or near-optimal solutions. 

## 9. Conclusions

This paper introduces RouteOpt, an advanced, modular, and open-source solver specifically designed to tackle the challenges of solving VRPs exactly. RouteOpt’s modular architecture not only streamlines development by removing the need for building intricate components from the ground up but also, empowers researchers to focus on refining targeted functionalities. Its open framework encourages innovation and user-driven customiza tion, making it an ideal platform for experimenting with novel techniques. RouteOpt also serves as a comprehen sive benchmarking platform capable of analyzing and decoding branching patterns in large-scale VRP instances. These insights can directly inform the design of new branching methods and optimization strategies. 

We believe that RouteOpt will lower the barrier to entry for researchers and practitioners interested in BPC based methods for VRPs, encouraging broader adoption of exact approaches in the field. By relieving users of repetitive implementation burdens, RouteOpt enables greater focus on creative algorithmic development and scientific exploration. Ultimately, we hope that RouteOpt will serve as both a research accelerator and a catalyst fo future advances in exact combinatorial optimization. 

## Acknowledgments

This work is partially supported by the National Science Foundation [Grant CMMI-2309667]. 

## Appendix. Detailed Numerical Results

This appendix presents detailed numerical results on CVRP (Table A.1) and VRPTW (Table A.2). Solver performance is evaluated by several metrics: optimality status (one for optimal and zero otherwise), root node processing time in 


Table A.1. Performance of CVRP for RouteOpt and VRPSolver


<table><tr><td rowspan="2">Instance</td><td colspan="6">RouteOpt</td><td colspan="6">VRPSolver</td></tr><tr><td>Opt</td><td>Root/s</td><td>Root gap/%</td><td>CPU/s</td><td>#Nodes</td><td>Gap/%</td><td>Opt</td><td>Root/s</td><td>Root gap/%</td><td>CPU/s</td><td>#Nodes</td><td>Gap/%</td></tr><tr><td>A-n62-k8</td><td>1</td><td>1.9</td><td>0.00</td><td>1.9</td><td>1</td><td>0.00</td><td>1</td><td>6.0</td><td>0.00</td><td>6.1</td><td>1</td><td>0.00</td></tr><tr><td>A-n63-k10</td><td>1</td><td>1.6</td><td>0.00</td><td>1.6</td><td>1</td><td>0.00</td><td>1</td><td>2.0</td><td>0.00</td><td>3.0</td><td>1</td><td>0.00</td></tr><tr><td>A-n63-k9</td><td>1</td><td>1.3</td><td>0.00</td><td>1.3</td><td>1</td><td>0.00</td><td>1</td><td>3.0</td><td>0.00</td><td>3.5</td><td>1</td><td>0.00</td></tr><tr><td>A-n64-k9</td><td>1</td><td>2.4</td><td>0.00</td><td>2.4</td><td>1</td><td>0.00</td><td>1</td><td>5.0</td><td>0.00</td><td>5.2</td><td>1</td><td>0.00</td></tr><tr><td>A-n65-k9</td><td>1</td><td>1.0</td><td>0.00</td><td>1.0</td><td>1</td><td>0.00</td><td>1</td><td>1.0</td><td>0.00</td><td>1.9</td><td>1</td><td>0.00</td></tr><tr><td>A-n69-k9</td><td>1</td><td>1.7</td><td>0.00</td><td>1.7</td><td>1</td><td>0.00</td><td>1</td><td>3.0</td><td>0.00</td><td>3.2</td><td>1</td><td>0.00</td></tr><tr><td>A-n80-k10</td><td>1</td><td>2.7</td><td>0.00</td><td>2.7</td><td>1</td><td>0.00</td><td>1</td><td>5.0</td><td>0.00</td><td>5.5</td><td>1</td><td>0.00</td></tr><tr><td>B-n63-k10</td><td>1</td><td>1.3</td><td>0.00</td><td>1.3</td><td>1</td><td>0.00</td><td>1</td><td>1.0</td><td>0.00</td><td>1.9</td><td>1</td><td>0.00</td></tr><tr><td>B-n64-k9</td><td>1</td><td>1.7</td><td>0.00</td><td>1.7</td><td>1</td><td>0.00</td><td>1</td><td>1.0</td><td>0.00</td><td>1.3</td><td>1</td><td>0.00</td></tr><tr><td>B-n66-k9</td><td>1</td><td>18.1</td><td>0.00</td><td>18.1</td><td>1</td><td>0.00</td><td>1</td><td>4.0</td><td>0.00</td><td>4.8</td><td>1</td><td>0.00</td></tr><tr><td>B-n67-k10</td><td>1</td><td>1.4</td><td>0.00</td><td>1.4</td><td>1</td><td>0.00</td><td>1</td><td>3.0</td><td>0.00</td><td>3.4</td><td>1</td><td>0.00</td></tr><tr><td>B-n68-k9</td><td>1</td><td>8.8</td><td>0.00</td><td>8.8</td><td>1</td><td>0.00</td><td>1</td><td>12.0</td><td>0.00</td><td>12.8</td><td>1</td><td>0.00</td></tr><tr><td>B-n78-k10</td><td>1</td><td>2.2</td><td>0.00</td><td>2.2</td><td>1</td><td>0.00</td><td>1</td><td>4.0</td><td>0.00</td><td>4.1</td><td>1</td><td>0.00</td></tr><tr><td>E-n76-k10</td><td>1</td><td>2.7</td><td>0.00</td><td>2.7</td><td>1</td><td>0.00</td><td>1</td><td>6.0</td><td>0.00</td><td>6.1</td><td>1</td><td>0.00</td></tr><tr><td>E-n76-k14</td><td>1</td><td>1.7</td><td>0.00</td><td>1.7</td><td>1</td><td>0.00</td><td>1</td><td>2.0</td><td>0.00</td><td>2.4</td><td>1</td><td>0.00</td></tr><tr><td>E-n76-k7</td><td>1</td><td>3.6</td><td>0.00</td><td>3.6</td><td>1</td><td>0.00</td><td>1</td><td>5.0</td><td>0.00</td><td>5.9</td><td>1</td><td>0.00</td></tr><tr><td>E-n76-k8</td><td>1</td><td>2.4</td><td>0.00</td><td>2.4</td><td>1</td><td>0.00</td><td>1</td><td>9.0</td><td>0.00</td><td>9.1</td><td>1</td><td>0.00</td></tr><tr><td>E-n101-k14</td><td>1</td><td>5.6</td><td>0.00</td><td>5.6</td><td>1</td><td>0.00</td><td>1</td><td>8.0</td><td>0.00</td><td>8.6</td><td>1</td><td>0.00</td></tr><tr><td>E-n101-k8</td><td>1</td><td>15.9</td><td>0.00</td><td>15.9</td><td>1</td><td>0.00</td><td>1</td><td>22.0</td><td>0.00</td><td>22.7</td><td>1</td><td>0.00</td></tr><tr><td>F-n72-k4</td><td>1</td><td>8.4</td><td>0.00</td><td>8.4</td><td>1</td><td>0.00</td><td>1</td><td>12.0</td><td>0.00</td><td>12.1</td><td>1</td><td>0.00</td></tr><tr><td>F-n135-k7</td><td>1</td><td>1,646.5</td><td>0.06</td><td>1,909.6</td><td>3</td><td>0.00</td><td>0</td><td>2,737.0</td><td>0.43</td><td>3,600.0</td><td>3</td><td>0.43</td></tr><tr><td>M-n101-k10</td><td>1</td><td>1.8</td><td>0.00</td><td>1.8</td><td>1</td><td>0.00</td><td>1</td><td>1.0</td><td>0.00</td><td>1.2</td><td>1</td><td>0.00</td></tr><tr><td>M-n121-k7</td><td>1</td><td>11.2</td><td>0.00</td><td>11.2</td><td>1</td><td>0.00</td><td>1</td><td>20.0</td><td>0.00</td><td>20.8</td><td>1</td><td>0.00</td></tr><tr><td>M-n151-k12</td><td>1</td><td>24.5</td><td>0.00</td><td>24.5</td><td>1</td><td>0.00</td><td>1</td><td>61.0</td><td>0.00</td><td>61.4</td><td>1</td><td>0.00</td></tr><tr><td>M-n200-k16</td><td>1</td><td>214.6</td><td>0.57</td><td>1,266.6</td><td>43</td><td>0.00</td><td>1</td><td>227.0</td><td>0.47</td><td>2,355.7</td><td>29</td><td>0.00</td></tr><tr><td>P-n65-k10</td><td>1</td><td>0.7</td><td>0.00</td><td>0.7</td><td>1</td><td>0.00</td><td>1</td><td>1.0</td><td>0.00</td><td>1.3</td><td>1</td><td>0.00</td></tr><tr><td>P-n70-k10</td><td>1</td><td>1.9</td><td>0.00</td><td>1.9</td><td>1</td><td>0.00</td><td>1</td><td>3.0</td><td>0.00</td><td>3.3</td><td>1</td><td>0.00</td></tr><tr><td>P-n76-k4</td><td>1</td><td>4.7</td><td>0.00</td><td>4.7</td><td>1</td><td>0.00</td><td>1</td><td>6.0</td><td>0.00</td><td>6.2</td><td>1</td><td>0.00</td></tr><tr><td>P-n76-k5</td><td>1</td><td>7.7</td><td>0.00</td><td>7.7</td><td>1</td><td>0.00</td><td>1</td><td>15.0</td><td>0.00</td><td>15.7</td><td>1</td><td>0.00</td></tr><tr><td>P-n101-k4</td><td>1</td><td>23.2</td><td>0.00</td><td>23.2</td><td>1</td><td>0.00</td><td>1</td><td>39.0</td><td>0.00</td><td>40.0</td><td>1</td><td>0.00</td></tr><tr><td>X-n101-k25</td><td>1</td><td>1.4</td><td>0.00</td><td>1.4</td><td>1</td><td>0.00</td><td>1</td><td>2.0</td><td>0.00</td><td>2.8</td><td>1</td><td>0.00</td></tr><tr><td>X-n106-k14</td><td>1</td><td>11.4</td><td>0.00</td><td>11.4</td><td>1</td><td>0.00</td><td>1</td><td>21.0</td><td>0.00</td><td>21.6</td><td>1</td><td>0.00</td></tr><tr><td>X-n110-k13</td><td>1</td><td>3.2</td><td>0.00</td><td>3.2</td><td>1</td><td>0.00</td><td>1</td><td>5.0</td><td>0.00</td><td>5.7</td><td>1</td><td>0.00</td></tr><tr><td>X-n115-k10</td><td>1</td><td>18.6</td><td>0.00</td><td>18.6</td><td>1</td><td>0.00</td><td>1</td><td>29.0</td><td>0.00</td><td>29.7</td><td>1</td><td>0.00</td></tr><tr><td>X-n120-k6</td><td>1</td><td>98.0</td><td>0.36</td><td>765.8</td><td>9</td><td>0.00</td><td>1</td><td>482.0</td><td>0.29</td><td>2,705.9</td><td>9</td><td>0.00</td></tr><tr><td>X-n125-k30</td><td>1</td><td>18.3</td><td>0.06</td><td>51.1</td><td>3</td><td>0.00</td><td>1</td><td>35.0</td><td>0.00</td><td>35.2</td><td>1</td><td>0.00</td></tr><tr><td>X-n129-k18</td><td>1</td><td>16.8</td><td>0.12</td><td>51.8</td><td>3</td><td>0.00</td><td>1</td><td>36.0</td><td>0.10</td><td>78.2</td><td>5</td><td>0.00</td></tr><tr><td>X-n134-k13</td><td>1</td><td>303.3</td><td>0.29</td><td>1,594.3</td><td>11</td><td>0.00</td><td>0</td><td>96.0</td><td>0.61</td><td>3,600.0</td><td>41</td><td>0.33</td></tr><tr><td>X-n139-k10</td><td>1</td><td>73.0</td><td>0.00</td><td>73.0</td><td>1</td><td>0.00</td><td>1</td><td>174.0</td><td>0.00</td><td>174.9</td><td>1</td><td>0.00</td></tr><tr><td>X-n143-k7</td><td>0</td><td>385.0</td><td>0.35</td><td>3,600.0</td><td>24</td><td>0.19</td><td>0</td><td>1,024.0</td><td>0.30</td><td>3,600.0</td><td>9</td><td>0.19</td></tr><tr><td>X-n148-k46</td><td>1</td><td>3.5</td><td>0.00</td><td>3.5</td><td>1</td><td>0.00</td><td>1</td><td>6.0</td><td>0.00</td><td>6.1</td><td>1</td><td>0.00</td></tr><tr><td>X-n153-k22</td><td>1</td><td>160.6</td><td>0.10</td><td>441.4</td><td>13</td><td>0.00</td><td>1</td><td>310.0</td><td>0.08</td><td>442.4</td><td>11</td><td>0.00</td></tr><tr><td>X-n157-k13</td><td>1</td><td>16.2</td><td>0.00</td><td>16.2</td><td>1</td><td>0.00</td><td>1</td><td>20.0</td><td>0.00</td><td>20.7</td><td>1</td><td>0.00</td></tr><tr><td>X-n162-k11</td><td>1</td><td>254.4</td><td>0.21</td><td>611.2</td><td>5</td><td>0.00</td><td>1</td><td>975.0</td><td>0.05</td><td>1,264.7</td><td>3</td><td>0.00</td></tr><tr><td>X-n167-k10</td><td>1</td><td>195.9</td><td>0.00</td><td>195.9</td><td>1</td><td>0.00</td><td>1</td><td>403.0</td><td>0.00</td><td>403.5</td><td>1</td><td>0.00</td></tr><tr><td>X-n172-k51</td><td>1</td><td>37.1</td><td>0.11</td><td>53.1</td><td>3</td><td>0.00</td><td>1</td><td>34.0</td><td>0.11</td><td>49.7</td><td>3</td><td>0.00</td></tr><tr><td>X-n176-k26</td><td>1</td><td>30.1</td><td>0.18</td><td>72.4</td><td>7</td><td>0.00</td><td>1</td><td>70.0</td><td>0.12</td><td>268.8</td><td>13</td><td>0.00</td></tr><tr><td>X-n181-k23</td><td>1</td><td>33.9</td><td>0.13</td><td>111.9</td><td>13</td><td>0.00</td><td>1</td><td>35.0</td><td>0.15</td><td>260.7</td><td>15</td><td>0.00</td></tr><tr><td>X-n186-k15</td><td>0</td><td>176.6</td><td>0.40</td><td>3,600.0</td><td>23</td><td>0.18</td><td>0</td><td>423.0</td><td>0.32</td><td>3,600.0</td><td>11</td><td>0.25</td></tr><tr><td>X-n190-k8</td><td>0</td><td>309.9</td><td>0.17</td><td>3,600.0</td><td>15</td><td>0.11</td><td>0</td><td>1,464.0</td><td>0.14</td><td>3,600.0</td><td>5</td><td>0.13</td></tr><tr><td>X-n195-k51</td><td>1</td><td>21.1</td><td>0.17</td><td>46.3</td><td>5</td><td>0.00</td><td>1</td><td>28.0</td><td>0.14</td><td>60.5</td><td>3</td><td>0.00</td></tr><tr><td>X-n200-k36</td><td>1</td><td>40.1</td><td>0.08</td><td>447.4</td><td>79</td><td>0.00</td><td>1</td><td>87.0</td><td>0.08</td><td>603.8</td><td>37</td><td>0.00</td></tr><tr><td>Small</td><td>22/22</td><td>2.5</td><td>0.00</td><td>2.5</td><td>1.0</td><td>0.00</td><td>22/22</td><td>3.7</td><td>0.00</td><td>4.3</td><td>1.0</td><td>0.00</td></tr><tr><td>Medium</td><td>27/30</td><td>36.4</td><td>0.11</td><td>78.5</td><td>3.4</td><td>0.02</td><td>25/30</td><td>62.0</td><td>0.11</td><td>125.1</td><td>3.0</td><td>0.04</td></tr></table>


Note. CPU/s, total CPU computation time in seconds; Gap/%, final global gap percentage; #Nodes, number of explored nodes; Opt, optimality status (one for optimal and zero otherwise); Root gap/%, root node gap percentage; Root/s, root node processing time in seconds. 



Table A.2. Performance of VRPTW for RouteOpt and VRPSolver


<table><tr><td rowspan="2">Instance</td><td colspan="6">RouteOpt</td><td colspan="6">VRPSolver</td></tr><tr><td>Opt</td><td>Root/s</td><td>Root gap/%</td><td>CPU/s</td><td>#Nodes</td><td>Gap/%</td><td>Opt</td><td>Root/s</td><td>Root gap/%</td><td>CPU/s</td><td>#Nodes</td><td>Gap/%</td></tr><tr><td>C204</td><td>1</td><td>36.8</td><td>0.00</td><td>36.8</td><td>1</td><td>0.00</td><td>1</td><td>98.2</td><td>0.00</td><td>98.2</td><td>1</td><td>0.00</td></tr><tr><td>R204</td><td>1</td><td>64.7</td><td>0.00</td><td>64.7</td><td>1</td><td>0.00</td><td>1</td><td>83.0</td><td>0.00</td><td>83.0</td><td>1</td><td>0.00</td></tr><tr><td>R208</td><td>1</td><td>558.2</td><td>0.20</td><td>2,248.4</td><td>9</td><td>0.00</td><td>0</td><td>1,034.6</td><td>0.42</td><td>3,600.0</td><td>7</td><td>0.21</td></tr><tr><td>R209</td><td>1</td><td>30.0</td><td>0.00</td><td>30.0</td><td>1</td><td>0.00</td><td>1</td><td>82.5</td><td>0.00</td><td>82.5</td><td>1</td><td>0.00</td></tr><tr><td>R210</td><td>1</td><td>42.7</td><td>0.00</td><td>42.7</td><td>1</td><td>0.00</td><td>1</td><td>70.5</td><td>0.00</td><td>70.5</td><td>1</td><td>0.00</td></tr><tr><td>R211</td><td>1</td><td>32.5</td><td>0.00</td><td>32.5</td><td>1</td><td>0.00</td><td>1</td><td>171.5</td><td>0.00</td><td>171.5</td><td>1</td><td>0.00</td></tr><tr><td>RC204</td><td>1</td><td>46.7</td><td>0.00</td><td>46.7</td><td>1</td><td>0.00</td><td>1</td><td>127.1</td><td>0.00</td><td>127.1</td><td>1</td><td>0.00</td></tr><tr><td>RC208</td><td>1</td><td>47.2</td><td>0.00</td><td>47.2</td><td>1</td><td>0.00</td><td>1</td><td>167.9</td><td>0.00</td><td>167.9</td><td>1</td><td>0.00</td></tr><tr><td>C1_2_3</td><td>1</td><td>95.3</td><td>0.12</td><td>102.4</td><td>3</td><td>0.00</td><td>1</td><td>75.3</td><td>0.00</td><td>75.3</td><td>1</td><td>0.00</td></tr><tr><td>C1_2_4</td><td>1</td><td>113.7</td><td>0.00</td><td>113.7</td><td>1</td><td>0.00</td><td>1</td><td>98.4</td><td>0.00</td><td>98.4</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_1</td><td>1</td><td>26.2</td><td>0.00</td><td>26.2</td><td>1</td><td>0.00</td><td>1</td><td>94.1</td><td>0.00</td><td>94.1</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_2</td><td>1</td><td>87.6</td><td>0.17</td><td>171.2</td><td>3</td><td>0.00</td><td>1</td><td>115.7</td><td>0.00</td><td>115.7</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_3</td><td>0</td><td>1,261.5</td><td>0.47</td><td>3,600.0</td><td>8</td><td>0.31</td><td>0</td><td>262.4</td><td>1.13</td><td>3,600.0</td><td>17</td><td>0.91</td></tr><tr><td>C2_2_4</td><td>0</td><td>620.0</td><td>1.53</td><td>3,600.0</td><td>9</td><td>1.37</td><td>0</td><td>299.2</td><td>2.48</td><td>3,600.0</td><td>17</td><td>2.26</td></tr><tr><td>C2_2_5</td><td>1</td><td>83.1</td><td>0.09</td><td>87.1</td><td>3</td><td>0.00</td><td>1</td><td>88.5</td><td>0.00</td><td>88.5</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_6</td><td>1</td><td>124.3</td><td>0.00</td><td>124.3</td><td>1</td><td>0.00</td><td>1</td><td>153.5</td><td>0.00</td><td>153.5</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_7</td><td>1</td><td>65.0</td><td>0.00</td><td>65.0</td><td>1</td><td>0.00</td><td>1</td><td>64.9</td><td>0.00</td><td>64.9</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_8</td><td>1</td><td>78.7</td><td>0.00</td><td>78.7</td><td>1</td><td>0.00</td><td>1</td><td>81.2</td><td>0.00</td><td>81.2</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_9</td><td>1</td><td>207.5</td><td>0.00</td><td>207.5</td><td>1</td><td>0.00</td><td>1</td><td>137.9</td><td>0.00</td><td>137.9</td><td>1</td><td>0.00</td></tr><tr><td>C2_2_10</td><td>1</td><td>269.2</td><td>0.00</td><td>269.2</td><td>1</td><td>0.00</td><td>1</td><td>249.6</td><td>0.00</td><td>249.7</td><td>1</td><td>0.00</td></tr><tr><td>R1_2_1</td><td>1</td><td>5.2</td><td>0.00</td><td>5.2</td><td>1</td><td>0.00</td><td>1</td><td>10.4</td><td>0.15</td><td>71.5</td><td>27</td><td>0.00</td></tr><tr><td>R1_2_3</td><td>0</td><td>117.2</td><td>0.85</td><td>3,600.0</td><td>79</td><td>0.34</td><td>0</td><td>408.0</td><td>0.78</td><td>3,600.0</td><td>11</td><td>0.61</td></tr><tr><td>R1_2_4</td><td>0</td><td>159.1</td><td>0.55</td><td>3,600.0</td><td>146</td><td>0.28</td><td>0</td><td>787.4</td><td>0.49</td><td>3,600.0</td><td>9</td><td>0.36</td></tr><tr><td>R1_2_5</td><td>1</td><td>33.8</td><td>0.27</td><td>80.9</td><td>11</td><td>0.00</td><td>1</td><td>51.1</td><td>0.18</td><td>131.0</td><td>5</td><td>0.00</td></tr><tr><td>R1_2_6</td><td>1</td><td>127.6</td><td>0.47</td><td>816.2</td><td>53</td><td>0.00</td><td>1</td><td>161.1</td><td>0.39</td><td>1,278.3</td><td>37</td><td>0.00</td></tr><tr><td>R1_2_7</td><td>1</td><td>99.8</td><td>0.11</td><td>150.4</td><td>3</td><td>0.00</td><td>1</td><td>215.6</td><td>0.00</td><td>215.6</td><td>1</td><td>0.00</td></tr><tr><td>R1_2_8</td><td>1</td><td>212.5</td><td>0.26</td><td>380.1</td><td>7</td><td>0.00</td><td>1</td><td>1,245.3</td><td>0.17</td><td>1,708.5</td><td>7</td><td>0.00</td></tr><tr><td>R1_2_9</td><td>1</td><td>89.3</td><td>0.29</td><td>202.4</td><td>17</td><td>0.00</td><td>1</td><td>75.5</td><td>0.20</td><td>198.6</td><td>9</td><td>0.00</td></tr><tr><td>R1_2_10</td><td>1</td><td>89.3</td><td>0.56</td><td>851.2</td><td>61</td><td>0.00</td><td>1</td><td>188.4</td><td>0.44</td><td>1,240.4</td><td>35</td><td>0.00</td></tr><tr><td>R2_2_2</td><td>1</td><td>130.9</td><td>0.00</td><td>130.9</td><td>1</td><td>0.00</td><td>1</td><td>150.1</td><td>0.00</td><td>150.1</td><td>1</td><td>0.00</td></tr><tr><td>R2_2_3</td><td>1</td><td>731.6</td><td>0.04</td><td>956.6</td><td>3</td><td>0.00</td><td>1</td><td>2,456.8</td><td>0.00</td><td>2,456.8</td><td>1</td><td>0.00</td></tr><tr><td>R2_2_4</td><td>1</td><td>886.6</td><td>0.20</td><td>3,559.7</td><td>29</td><td>0.00</td><td>0</td><td>3,600.0</td><td>0.54</td><td>3,600.0</td><td>1</td><td>0.54</td></tr><tr><td>R2_2_5</td><td>1</td><td>254.3</td><td>0.03</td><td>259.3</td><td>3</td><td>0.00</td><td>1</td><td>385.0</td><td>0.00</td><td>385.0</td><td>1</td><td>0.00</td></tr><tr><td>R2_2_6</td><td>1</td><td>527.2</td><td>0.06</td><td>680.1</td><td>3</td><td>0.00</td><td>1</td><td>1,307.8</td><td>0.00</td><td>1,307.8</td><td>1</td><td>0.00</td></tr><tr><td>R2_2_7</td><td>1</td><td>1,532.5</td><td>0.00</td><td>1,532.5</td><td>1</td><td>0.00</td><td>0</td><td>3,600.0</td><td>0.81</td><td>3,600.0</td><td>1</td><td>0.81</td></tr><tr><td>R2_2_8</td><td>0</td><td>3,466.4</td><td>0.26</td><td>3,600.0</td><td>2</td><td>0.43</td><td>0</td><td>3,600.0</td><td>2.98</td><td>3,600.0</td><td>1</td><td>2.98</td></tr><tr><td>R2_2_9</td><td>1</td><td>186.8</td><td>0.09</td><td>342.5</td><td>5</td><td>0.00</td><td>1</td><td>320.5</td><td>0.00</td><td>320.5</td><td>1</td><td>0.00</td></tr><tr><td>R2_2_10</td><td>1</td><td>260.6</td><td>0.21</td><td>394.2</td><td>3</td><td>0.00</td><td>1</td><td>686.9</td><td>0.16</td><td>936.4</td><td>3</td><td>0.00</td></tr><tr><td>RC1_2_1</td><td>1</td><td>67.2</td><td>0.52</td><td>215.1</td><td>31</td><td>0.00</td><td>1</td><td>63.2</td><td>0.45</td><td>405.0</td><td>19</td><td>0.00</td></tr><tr><td>RC1_2_2</td><td>1</td><td>80.8</td><td>0.59</td><td>644.3</td><td>65</td><td>0.00</td><td>1</td><td>223.8</td><td>0.46</td><td>641.6</td><td>9</td><td>0.00</td></tr><tr><td>RC1_2_3</td><td>0</td><td>148.2</td><td>0.80</td><td>3,600.0</td><td>17</td><td>0.46</td><td>0</td><td>1,055.5</td><td>0.52</td><td>3,600.0</td><td>5</td><td>0.34</td></tr><tr><td>RC1_2_4</td><td>1</td><td>344.7</td><td>0.43</td><td>2,935.4</td><td>13</td><td>0.00</td><td>0</td><td>1,017.1</td><td>0.42</td><td>3,600.0</td><td>9</td><td>0.25</td></tr><tr><td>RC1_2_5</td><td>1</td><td>191.4</td><td>0.54</td><td>1,502.3</td><td>29</td><td>0.00</td><td>1</td><td>536.2</td><td>0.36</td><td>1,118.9</td><td>15</td><td>0.00</td></tr><tr><td>RC1_2_6</td><td>1</td><td>107.8</td><td>0.25</td><td>273.1</td><td>11</td><td>0.00</td><td>1</td><td>173.1</td><td>0.16</td><td>320.2</td><td>3</td><td>0.00</td></tr><tr><td>RC1_2_7</td><td>0</td><td>264.4</td><td>1.04</td><td>3,600.0</td><td>12</td><td>0.65</td><td>0</td><td>869.5</td><td>0.67</td><td>3,600.0</td><td>5</td><td>0.39</td></tr><tr><td>RC1_2_8</td><td>1</td><td>332.9</td><td>0.45</td><td>3,527.7</td><td>21</td><td>0.00</td><td>1</td><td>614.2</td><td>0.32</td><td>1,361.5</td><td>3</td><td>0.00</td></tr><tr><td>RC1_2_9</td><td>0</td><td>150.6</td><td>1.39</td><td>3,600.0</td><td>16</td><td>0.91</td><td>0</td><td>965.9</td><td>1.00</td><td>3,600.0</td><td>5</td><td>0.84</td></tr><tr><td>RC1_2_10</td><td>0</td><td>264.8</td><td>0.91</td><td>3,600.0</td><td>13</td><td>0.67</td><td>0</td><td>954.3</td><td>0.70</td><td>3,600.0</td><td>5</td><td>0.53</td></tr><tr><td>RC2_2_1</td><td>1</td><td>64.8</td><td>0.00</td><td>64.8</td><td>1</td><td>0.00</td><td>1</td><td>51.3</td><td>0.00</td><td>51.3</td><td>1</td><td>0.00</td></tr><tr><td>RC2_2_2</td><td>1</td><td>448.8</td><td>0.23</td><td>2,887.4</td><td>21</td><td>0.00</td><td>1</td><td>803.1</td><td>0.22</td><td>2,152.4</td><td>5</td><td>0.00</td></tr><tr><td>RC2_2_3</td><td>1</td><td>1,939.7</td><td>0.05</td><td>2,593.4</td><td>5</td><td>0.00</td><td>0</td><td>3,600.0</td><td>2.05</td><td>3,600.0</td><td>1</td><td>2.05</td></tr><tr><td>RC2_2_4</td><td>0</td><td>2,441.7</td><td>0.65</td><td>3,600.0</td><td>3</td><td>0.73</td><td>0</td><td>3,600.0</td><td>27.72</td><td>3,600.0</td><td>1</td><td>27.72</td></tr><tr><td>RC2_2_5</td><td>1</td><td>341.1</td><td>0.00</td><td>341.1</td><td>1</td><td>0.00</td><td>1</td><td>187.7</td><td>0.00</td><td>187.8</td><td>1</td><td>0.00</td></tr><tr><td>RC2_2_6</td><td>1</td><td>301.9</td><td>0.00</td><td>301.9</td><td>1</td><td>0.00</td><td>1</td><td>226.7</td><td>0.00</td><td>226.7</td><td>1</td><td>0.00</td></tr><tr><td>RC2_2_7</td><td>1</td><td>312.2</td><td>0.42</td><td>2,071.3</td><td>21</td><td>0.00</td><td>0</td><td>1,393.2</td><td>0.59</td><td>3,600.0</td><td>5</td><td>0.21</td></tr><tr><td>RC2_2_8</td><td>1</td><td>895.6</td><td>0.00</td><td>1,731.6</td><td>5</td><td>0.00</td><td>0</td><td>3,600.0</td><td>0.51</td><td>3,600.0</td><td>1</td><td>0.51</td></tr><tr><td>RC2_2_9</td><td>0</td><td>3,600.0</td><td>1.06</td><td>3,600.0</td><td>1</td><td>1.06</td><td>0</td><td>3,600.0</td><td>2.57</td><td>3,600.0</td><td>1</td><td>2.57</td></tr><tr><td>RC2_2_10</td><td>0</td><td>3,478.8</td><td>1.18</td><td>3,600.0</td><td>2</td><td>1.69</td><td>0</td><td>3,600.0</td><td>3.22</td><td>3,600.0</td><td>1</td><td>3.22</td></tr><tr><td>Solomon</td><td>8/8</td><td>57.6</td><td>0.02</td><td>68.6</td><td>1.3</td><td>0.00</td><td>7/8</td><td>143.5</td><td>0.05</td><td>167.7</td><td>1.3</td><td>0.03</td></tr><tr><td>HG</td><td>38/50</td><td>229.9</td><td>0.34</td><td>592.9</td><td>5.4</td><td>0.18</td><td>32/50</td><td>389.1</td><td>1.06</td><td>720.1</td><td>2.7</td><td>0.94</td></tr></table>


Note. CPU/s, total CPU computation time in seconds; Gap/%, final global gap percentage; #Nodes, number of explored nodes; Opt, optimality status (one for optimal and zero otherwise); Root gap/%, root node gap percentage; Root/s, root node processing time in seconds. 


seconds, root node gap, total CPU computation time in seconds, number of explored nodes, and final global gap. The last two rows summarize performance metrics, reporting the gap as an arithmetic average and all other metrics as geometric means. 

## References



Baldacci R, Christofides N, Mingozzi A (2008) An exact algorithm for the vehicle routing problem based on the set partitioning formulation with additional cuts. Math. Programming 115:351–385. 





Baldacci R, Mingozzi A, Roberti R (2011) New route relaxation and pricing strategies for the vehicle routing problem. Oper. Res. 59(5): 1269–1283. 





Balster I, Bulho˜es T, Munari P, Pessoa AA, Sadykov R (2023) A new family of route formulations for split delivery vehicle routing problems. Transportation Sci. 57(5):1359–1378. 





Bulhoes T, Sadykov R, Uchoa E (2018a) A branch-and-price algorithm for the minimum latency problem. Comput. Oper. Res. 93:66–78. 





Bulhoes T, Pessoa A, Protti F, Uchoa E (2018b) On the complete set packing and set partitioning polytopes: Properties and rank 1 facets. Oper. Res. Lett. 46(4):389–392. 





Chva´tal V (1973) Edmonds polytopes and a hierarchy of combinatorial problems. Discrete Math. 4(4):305–337. 





Dantzig GB, Ramser JH (1959) The truck dispatching problem. Management Sci. 6(1):80–91. 





Errami N, Queiroga E, Sadykov R, Uchoa E (2024) Vrpsolvereasy: A Python library for the exact solution of a rich vehicle routing problem INFORMS J. Comput. 36(4):956–965 





Gurobi Optimization LLC (2023) Gurobi 10.0 documentation. Accessed May 13, 2026, https://www.gurobi.com/documentation/10.0/ refman/index.html. 





IBM Corporation (2023) IBM ILOG CPLEX Optimization Studio. Accessed May 13, 2026, https://www.ibm.com/docs/en/icos/22.1 0?topic=manuals-cplex-users-manual. 





Irnich S, Desaulniers G, Desrosiers J, Hadjar A (2010) Path-reduced costs for eliminating arcs in routing and scheduling. INFORMS J. Comput. 22(2):297–313. 





Kim H, Park J, Kwon C (2024) A neural separation algorithm for the rounded capacity inequalities. INFORMS J. Comput. 36(4):987–1005 





Lima I, Uchoa E, Pecin D, Pessoa A, Poggi M, Vidal T, Subramanian A, Oliveira D, Queiroga E (2014) CVRPLib: Capacitated vehicle routing problem library. Accessed, http://vrp.galgos.inf.puc-rio.br/index.php/en/updates. 





Lin W, He Z, Jiang S, Ma F, Su Z, Lu ¨ Z (2026) Alkaid-SDVRP: An efficient open-source solver for the vehicle routing problem with split deliveries. INFORMS J. Comput. 38(1):150–164. 





Lysgaard J (2003) CVRPSEP: A package of separation routines for the capacitated vehicle routing problem. Technical report, Department of Management Science and Logistics, Aarhus School of Business and Social Sciences, Aarhus University, Aarhus, Denmark. 





Lysgaard J, Letchford AN, Eglese RW (2004) A new branch-and-cut algorithm for the capacitated vehicle routing problem. Math. Programming 100:423–445. 





Martinelli R, Pecin D, Poggi M (2014) Efficient elementary and restricted non-elementary route pricing. Eur. J. Oper. Res. 239(1):102–111 





Morabit M. Desaulniers G. Lodi A (2021) Machine-learning-based column selection for column generation. Transportation Sci. 55(4):815–831 





Naddef D, Rinaldi G (2002) Branch-and-cut algorithms for the capacitated VRP. Toth P, Vigo D, eds. The Vehicle Routing Problem, SIAM Mono graphs on Discrete Mathematics and Applications (SIAM, Philadelphia), 53–84. 





Pecin D, Contardo C, Desaulniers G, Uchoa E (2017a) New enhancements for the exact solution of the vehicle routing problem with time win dows. INFORMS J. Comput. 29(3):489–502. 





Pecin D, Pessoa A, Poggi M, Uchoa E, Santos H (2017b) Limited memory rank-1 cuts for vehicle routing problems. Oper. Res. Lett. 45(3): 206–209. 





Pessoa A, Sadykov R, Uchoa E (2021) Solving bin packing problems using VRPSolver models. Oper. Res. Forum 2:20 





Pessoa A, Sadykov R, Uchoa E, Vanderbeck F (2018) Automation and combination of linear-programming based stabilization techniques in column generation. INFORMS J. Comput. 30(2):339–360. 





Pessoa A, Sadykov R, Uchoa E, Vanderbeck F (2020) A generic exact solver for vehicle routing and related problems. Math. Programming 183:483–523. 





Righini G, Salani M (2006) Symmetry helps: Bounded bi-directional dynamic programming for the elementary shortest path problem with resource constraints. Discrete Optim. 3(3):255–273. 





Roberti R, Mingozzi A (2014) Dynamic ng-path relaxation for the delivery man problem. Transportation Sci. 48(3):413–424. 





Sadykov R, Vanderbeck F (2021) BaPCod—A generic branch-and-price code. Technical report, Bordeaux-Sud-Ouest Inria Research Centre, Talence, France. 





Sadykov R, Uchoa E, Pessoa A (2021) A bucket graph–based labeling algorithm with application to vehicle routing. Transportation Sci. 55(1): 4–28. 





Silva JMP, Uchoa E, Subramanian A (2024) Cluster branching for vehicle routing problems. Accessed May 13, 2026, https://optimization online.org/?p=27150. 





Solomon MM (1987) Algorithms for the vehicle routing and scheduling problems with time window constraints. Oper. Res. 35(2):254–265. 





Vidal T (2022) Hybrid genetic search for the CVRP: Open-source implementation and swap* neighborhood. Comput. Oper. Res. 140:105643 





Vidal T, Crainic TG, Gendreau M, Prins C (2013) A hybrid genetic algorithm with adaptive diversity management for a large class of vehicle routing problems with time-windows. Comput. Oper. Res. 40(1):475–489. 





Vidal T, Crainic TG, Gendreau M, Lahrichi N, Rei W (2012) A hybrid genetic algorithm for multidepot and periodic vehicle routing problems Oper. Res. 60(3):611–624. 





Wouda NA, Lan L, Kool W (2024) PYVRP: A high-performance VRP solver package. INFORMS J. Comput. 36(4):943–955. 





Yang Y (2023) An exact price-cut-and-enumerate method for the capacitated multitrip vehicle routing problem with time windows. Transpor tation Sci. 57(1):230–251. 





Yang Y (2025) Deluxing: Deep Lagrangian underestimate fixing for column-generation-based exact methods. Oper. Res. 73(3):1184–1207. 





You Z (2025) RouteOpt documentation. Accessed May 13, 2026, https://zhengzhong-you.github.io/RouteOpt-Docs/rank1_cuts/r1c_index html#id72 





You Z, Yang Y (2026) RouteOpt: An open-source modular exact solver for vehicle routing problems, https://github.com/INFORMSJoC/2025. 1415. 





You Z, Yang Y, Wang X, Yin W (2026) Two-stage learning to branch in branch-price-and-cut algorithms for solving vehicle routing problem exactly. Oper. Res., ePub ahead of print February 24, https://doi.org/10.1287/opre.2023.0615. 



Copyright of INFORMS Journal on Computing is the property of INFORMS: Institute for Operations Research & the Management Sciences and its content may not be copied or emailed to multiple sites without the copyright holder's express written permission. Additionally, content may not be used with any artificial intelligence tools or machine learning technologies. However, users may print, download, or email articles for individual use. 