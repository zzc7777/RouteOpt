# A Bucket Graph–Based Labeling Algorithm with Application to Vehicle Routing

Ruslan Sadykov,<sup>a,b</sup> Eduardo Uchoa,<sup>c</sup> Artur Pessoa<sup>c</sup> 

<sup>a</sup> INRIA Bordeaux Sud-Ouest, 33405 Talence, France; <sup>b</sup> Institute of Mathematics, University of Bordeaux, 3340 Talence, France; <sup>c</sup> Engenharia de Produção, Universidade Federal Fluminense, Niteroi, Brasil, 24210-240´ 

Contact: ruslan.sadykov@inria.fr, https://orcid.org/0000-0002-4862-2226 (RS); uchoa@producao.uff.br (EU); artur@producao.uff.br, https://orcid.org/0000-0002-7421-4744 (AP) 

Received: October 19, 2017 Revised: September 25, 2018; July 30, 2019; February 12, 2020 Accepted: February 21, 2020 Published Online in Articles in Advance: October 5, 2020 

https://doi.org/10.1287/trsc.2020.0985 

Copyright: © 2020 INFORMS 

Abstract. We consider the shortest path problem with resource constraints arising as a subproblem in state-of-the-art branch-cut-and-price algorithms for vehicle routing problems. We propose a variant of the bidirectional label-correcting algorithm in which the labels are stored and extended according to the so-called bucket graph. This organization of labels helps to significantly decrease the number of dominance checks and the running time of the algorithm. We also show how the forward/backward route symmetry can be exploited and how to eliminate arcs from the bucket graph using reduced costs. The proposed algorithm can be especially beneficial for vehicle routing instances with large vehicle capacity and/or with time window constraints. Computational experiments were performed on instances from the distance-constrained vehicle routing problem, including multidepot and site-dependent variants, on the vehicle routing problem with time windows, and on the “nightmare” instances of the heterogeneous fleet vehicle routing problem. Significant improvements over the best algorithms in the literature were achieved, and many instances could be solved for the first time. 

Funding: This work was supported by Fundação Carlos Chagas Filho de Amparo a Pesquisa do Estado` do Rio de Janeiro, FAPERJ E26/110.075/2014. 

Supplemental Material: The online appendix is available at https://doi.org/10.1287/trsc.2020.0985. 

Keywords: labeling algorithm • shortest path with resource constraints • routing 

## 1. Introduction

The best known exact approach for many classical variants of the vehicle routing problem (VRP) is a branch-cut-and-price (BCP) algorithm in which the master problem contains route variables, customer visiting constraints, and additional cuts. Because of their exponential number, the route variables are generated dynamically by solving pricing subproblems modeled as shortest path problems with resource constraints (SPPRC). In those problems, one looks for least cost paths joining a source vertex with a sink vertex such that the accumulated consumption of resources along the path respects given lower and upper limits. Labeling algorithms from dynamic programming are the most common way of solving those problems. 

Labeling algorithms differ from the traditional “table filling” dynamic programming algorithms, because they only store (as labels) the reachable states, those representing feasible partial paths. More importantly, whereas traditional dynamic programming only performs dominance over identical states, labeling algorithms perform dominance between labels corresponding to nonidentical states. Typically, a label L representing a path P<sup>L</sup> is dominated, and therefore $P ^ { L ^ { \prime } }$ eliminated, if there is another label L representing a path $P ^ { L }$ that ends at the same vertex, is not more costly, and does not use more resources than $P ^ { L ^ { \prime } }$ Monodirectional labeling algorithms start from a single label representing a null path at the source vertex. At each step, a nonextended label L is extended to additional labels corresponding to the possible ways of adding a single arc to $P ^ { L }$ . Dominance may be used to eliminate labels, avoiding future extensions. If there are no nonextended labels, the algorithm stops and optimal solutions are found among the labels representing paths ending at the sink vertex. The general labeling algorithm has several degrees of freedom. In particular, there are many possible ways of choosing the next label to be extended and how often and how extensively domi nance checks are performed. Particular labeling algorithms are classified as being either label-setting or label-correcting. The defining property of a labelsetting algorithm is that it only extends labels that can never be dominated by another label created after that extension. 

There is a large literature on labeling algorithms for SPPRC; we refer to Irnich and Desaulniers (2005) and Pugliese and Guerriero (2013) for surveys 

However, somehow surprisingly, not so many papers discuss in depth issues related to label organization and dominance strategies. In fact, even though the pricing time is a bottleneck in many BCP algorithms for VRP, authors often skip the details of the labeling algorithm used. An exception is the recent algorithm for the capacitated VRP (CVRP) in Pecin et al. (2017b). In that case, assuming that the consumption of the single resource (capacity) is given by a positive integer, labels are organized in buckets corresponding to each possible consumption. Dominance is only performed for labels in the same bucket, which by definition have the same resource consumption. However, that approach cannot be applied in cases where resource consumption is given by an arbitrary real number. In fact, if the consumption is given by larger integers, the algorithm becomes slow, since few dominance checks are performed and too many undetected dominated labels are expanded. The opposite approach is to perform full label dominance after each extension. In this case, the running time may suffer a lot from too many dominance checks with negative results. 

Lozano and Medaglia (2013) proposed the so-called pulse algorithm for the SPPRC. Pulse is better viewed as a depth-first branch-and-bound search algorithm than as a dynamic programming labeling algorithm, because it uses dominance in a severely limited way—each vertex only keeps a handful of partial paths for performing dominance checks. The main mechanism in the pulse algorithm for trying to avoid an exponential explosion in its search is bounding, that is, a partial path is pruned if it can be shown that it cannot be extended into a full path ending at the sink vertex that costs less than the currently best known source-tosink path. Pulse produced excellent results, much better than label-setting algorithms, on some standalone SPPRCs where all arc costs are positive. The last feature helps the pulse algorithm because it allows good bounds to be computed by Dijkstra’s-like algorithms. Up to now, as far as we know, the pulse algorithm has only been tested as a subproblem solver in a column generation algorithm for VRP with time windows (VRPTW) in Lozano, Duque, and Medaglia (2015). The SPPRCs that appear in that context are more complex, because the master dual variables may make arc costs negative. The test was calculating the elementary route bound on instances with 100 customers. The variant of the pulse algorithm used for the VRPTW only performs an even more basic form of dominance called rollback pruning. However, it proposes a more sophisticated bounding mechanism that only considers the time resource. The obtained results compare favorably with the labeling algorithms of Desaulniers, Lessard, and Hadjar (2008) and Baldacci, Mingozzi, and Roberti (2011). It is not known how the pulse algorithm would perform on larger instances or how it would handle the modifications in the SPPRC induced by cuts, essential for modern BCP algorithms for VRP. 

The main original contributions of this paper are the following: 

• In Section 3, a new variant of the labeling algo rithm is proposed. The approach relies on a so-called bucket graph, consisting of buckets and bucket arcs. Labels for paths ending at the same vertex of the original graph and having similar resource consumption are grouped together in buckets. A bucket arc links two buckets if a label in the first one can be possibly extended to a label in the second. Labels within the same bucket are always pairwise undominated, that is, there is no label which is dominated by another label in the same bucket. However, dominance checks between labels in different buckets are only performed before an extension, when it is checked whether a candidate labe to be extended is dominated or not. Moreover, this interbucket dominance uses bounds on the costs of all labels in a bucket, avoiding many unnecessary checks. The key parameter of the algorithm is the step size, used for determining the resource consumption intervals that define each bucket. If the step size is sufficiently small, the bucket graph is acyclic and the algorithm becomes a label-setting algorithm. In general, the bucket graph has cycles and the algorithm is a labelcorrecting algorithm. A bidirectional version of the bucket graph–based labeling algorithm is also proposed (since Righini and Salani 2006 it is known that bidirectional labeling algorithms are often more efficient than their monodirectional counterparts). The concatenation of labels step is also accelerated by bounds on the costs. Bidirectional search also allows us to exploit the forward/backward symmetry o routes that exists on some VRP variants, for example, on the classical CVRP. In those cases, the backward search is replaced by a “reversed copy” of the forward search, reducing the running time. 

• In Section 4, a procedure for accelerating the labeling algorithm by removing arcs from the bucket graph using reduced cost arguments is proposed. We call it the bucket arc elimination procedure. The concept of jump bucket arcs is introduced. The new fixing procedure generalizes both the procedures in Irnich et al. (2010) and in Pessoa et al. (2010). After a fixing, some bucket arcs are eliminated, and thus, the number of extensions and the running times are reduced in future calls to the labeling algorithm. 

The new labeling algorithm was embedded as the pricing method in a modern BCP algorithm for VRPs. Our BCP also includes many ingredients found in other state-of-the-art BCP algorithms, like dynamic ng-path relaxation, rounded capacity cuts, limited arc memory rank-1 cuts, automatic dual price smoothing stabilization, a procedure for enumerating elementary paths, and multiphase strong branching with pseudocosts. The algorithm was computationally tested on several VRP variants with time window constraints and/or large vehicle capacity. Pretests have shown that the labeling algorithm could perform very well, but that performance is rather sensitive to the choice of the step size parameter. Moreover, the good step size values varied strongly from instance to instance. Therefore, a simple but effective scheme for an automatic dynamic adjustment of the step size was devised. Other specific experiments indicate that, for the hardest instances, the new bucket arc fixing procedure can indeed be better than existing schemes for fixing arcs by reduced cost. Lastly, extensive experiments show that the final BCP algorithm significantly outperforms other recent stateof-the-art algorithms for the VRPTW and the multidepot VRP with distance constraints (MDVRPDC). Moreover, our algorithm is the first exact approach that can handle medium-sized instances of the classical distance-constrained VRP (DCVRP), including the sitedependent variant. Our algorithm is also the first exact method successfully applied for a set of “nightmare” instances of the heterogeneous fleet VRP (HFVRP). 

The remainder of this article has the following structure. Section 2 defines the exact SPPRC solved in this paper. It is explained how this SPPRC arises as a pricing subproblem for solving a HFVRP with time windows (HFVRPTW) with a column-and-cut generation algorithm. Section 3 describes the bucket graph labeling algorithm proposed in this work. Section 4 presents the bucket arc elimination procedure and introduces the key concept of jump bucket arcs. Section $5$ presents the complete BCP algorithm for HFVRPTW where the bucket graph labeling algorithm was embedded. Section 6 presents results of the computational experiments. There are experiments devised to assess the impact of the bucket step size and experiments for measuring the impact of the introduced bucket arc elimination procedure. Moreover, extensive experiments evaluate the overall BCP performance on several classical VRP variants that are particular cases of the HFVRPTW. Section 7 contains final remarks. Finally, very detailed computational results are presented in the online appendix. 

## 2. Application

In order to give a precise definition of the SPPRC addressed in this paper, we first need to define its application: defining the family of VRPs that we ultimately want to solve and outlining the column-andcut generation algorithm where that SPPRC arises as the pricing subproblem. 

## 2.1. HFVRPTW Definition

The HFVRPTW (Jiang et al. 2014) is defined as follows. Let $G = ( V , A )$ be a directed graph with vertex set $V = \{ 0 , \ldots , n + 1 \}$ . For convenience, the depot vertex is split into the source vertex 0 and the sink vertex $n + 1 ;$ vertices in $V ^ { \prime } = V \setminus \{ 0 , n + 1 \}$ represent the n cus tomers. The arc set is defined as $\bar { A = } \{ ( v , v ^ { \prime } ) : v , v ^ { \prime } \in V ,$ $v \neq v ^ { \prime } , v \neq n + 1 , v ^ { \prime } \neq 0 , ( v , v ^ { \prime } ) \neq ( 0 , n + 1 ) \}$ . Each vertex $v \in V$ has a demand $w _ { v } ,$ a service time (duration) $s _ { v } ,$ and a time window $[ l _ { v } , u _ { v } ]$ associated with it. Demands and service times are nonnegative for cus tomers $v \in V ^ { \prime }$ and equal to zero for the depot vertices, and $l _ { 0 }$ is assumed to be equal to 0. The fleet is composed of a set M of different types of vehicles. For each $m \in M$ , there are $U _ { m }$ available vehicles, each with a capacity $W _ { m }$ . Let $W = \operatorname* { m a x } \{ W _ { m } : m \in M \}$ be the largest capacity. Every vehicle type is associated with a fixed cost denoted by $f _ { m }$ . For each arc a A and $m \in M$ there are a cost $c _ { a } ^ { m }$ and a nonnegative travel time $t _ { a } ^ { m }$ associated to the traversal of this arc by a vehicle of type m. We assume that G does not have any cycle, where for some vehicle type, all vertices have zero demands and zero service times and all arcs have zero time. A route $P = ( v _ { 0 } = 0 , v _ { 1 } , \ldots , v _ { k } , v _ { k + 1 } = n + 1 )$ for a vehicle of type m M is a walk, where $v _ { 1 } , \ldots , v _ { k }$ are not necessarily distinct customers in $V ^ { \prime }$ and is said to be feasible if (1) it satisfies vehicle capacity: $\begin{array} { r } { \sum _ { j = 1 } ^ { k } w _ { v _ { j } } \leq W _ { m } ; } \end{array}$ and (2) the earliest start of service time ${ \dot { S } } _ { j }$ at every visited vertex $v _ { j } , 0 \leq j \leq k + 1$ , falls within the corresponding time window: $l _ { v _ { j } } \le S _ { j } \le u _ { v _ { j } }$ , where $S _ { 0 } = l _ { 0 }$ and $S _ { j } =$ max $\{ l _ { v _ { j } } , S _ { j - 1 } + s _ { v _ { j - 1 } } + t _ { ( v _ { j - 1 } , v _ { j } ) } ^ { \dot { m } } \}$ . A vehicle may arrive at a vertex before the beginning of its time window and wait, but it cannot arrive after the end of the time window. The cost of route P is calculated as $\begin{array} { r } { f _ { m } + \sum _ { j = 1 } ^ { k + 1 } c _ { ( v _ { j - 1 } , v _ { j } ) } ^ { m } . } \end{array}$ A route is said to be elementary if the same customer is not visited more than once. The objective is to determine a set of feasible elementary routes with minimal total cost such that (i) each customer is visited by exactly one route and (ii) the number of routes associated to a vehicle type does not exceed its availability. Clearly, some of most classical VRP variants, like CVRP, VRPTW, and HFVRP, are particular cases of HFVRPTW. Other variants that are also particular cases of HFVRPTW are listed below. 

In the MDVRP, the vehicles themselves are identical, they only differ by being attached to different depots. It can be easily modeled as a HFVRP by associating each depot to a vehicle type and setting costs $c _ { ( 0 , v ) } ^ { m }$ and times $t _ { ( 0 , v ) } ^ { m }$ for leaving the depot, together with costs $c _ { ( v , n + 1 ) } ^ { m }$ and times $t _ { ( v , n + 1 ) } ^ { m }$ for entering the depot, that depend on $m \in M$ . The site-dependent VRP (SDVRP), a variant where vehicles differ only by the subset of the customers that they can visit, is also easily modeled as a HFVRP by setting infinite costs for $c _ { ( v , v ^ { \prime } ) } ^ { m }$ if vehicle type m cannot visit either v or $v ^ { \prime } .$ 

The classical DCVRP can also be solved as a HFVRPTW. In this variant, besides the capacity constraint, the route length is also forbidden to be above a certain threshold D. Since service times are included in that “length,” this is actually a time limit for returning to the depot. It can be modeled by setting time window [0<sup>,</sup> D] for every $v \in V$ 

## 2.2. Set Partitioning Formulation and Cuts

Let $\Omega _ { m }$ be the set of all feasible elementary routes for vehicle type m  M. A set-partitioning formulation (SPF) over those sets would lead to a hard pricing problem, probably intractable on large instances. A more tractable (but weaker) formulation can be defined using a relaxed set of routes that includes some nonelementary routes, that is, routes in which some customers are visited more than once. In this work we assume that the ng-route relaxation (Baldacci, Mingozzi, and Roberti 2011), a route relaxation with a good tradeoff between formulation strength and pricing difficulty, is being used. Let $N _ { v } \subseteq V ^ { \prime }$ be the neighborhood of $v \in V ^ { \prime } .$ , typically containing the closest customers to v. An ng-route can only revisit a customer $v ,$ forming a cycle, if the cycle contains another customer $\boldsymbol { v ^ { \prime } }$ with v / $N _ { v ^ { \prime } }$ . In many cases, reasonably small neighborhoods (for example, with $\left| N _ { v } \right| = 8 )$ already provide bounds that are close to those that would be obtained by pricing elementary routes (Poggi and Uchoa 2014; Contardo, Desaulniers, and Lessard 2015). 

Let $\Omega _ { m } ^ { N } \supseteq \Omega _ { m }$ be the set of ng-routes for vehicle type $m \in M$ with respect to a given set of neighborhoods $N = \left( N _ { 1 } , \ldots , N _ { n } \right)$ . We denote by $c _ { P }$ the cost of route $P \in \Omega _ { m } ^ { N } , \mathrm { ~ b y ~ } x _ { ( v , v ^ { \prime } ) } ^ { P }$ the number of times arc $( v , v ^ { \prime } ) \in A$ appears in route $P ,$ and by $\begin{array} { r } { y _ { v } ^ { P } = \sum _ { a \in \delta ^ { + } ( \{ v \} ) \cup \delta ^ { - } ( \{ v \} ) } x _ { a } ^ { P } / 2 } \end{array}$ the number of times vertex $v \in V ^ { \prime }$ is visited in route $P .$ (This “symmetr $c ^ { \prime \prime }$ definition of $y _ { v } ^ { P }$ is exploited in Section $\dot { 3 } . 6 . )$ Let $\lambda _ { P }$ be a binary variable indicating whether route $P$ is selected or not in the solution. The SPF for the HFVRPTW considered in this paper is the following: 

$$
\text {(SPF)} \min \sum_ {m \in M} \sum_ {P \in \Omega_ {m} ^ {N}} c _ {P} \lambda_ {P}\tag{1}
$$

$$
\text { subject   to } \sum_ {m \in M} \sum_ {P \in \Omega_ {m} ^ {N}} y _ {v} ^ {P} \lambda_ {P} = 1 \quad \forall v \in V ^ {\prime},\tag{2}
$$

$$
\sum_ {P \in \Omega_ {m} ^ {N}} \lambda_ {P} \leq U _ {m} \quad \forall m \in M,\tag{3}
$$

$$
\lambda_ {P} \in \{0, 1 \} \quad \forall m \in M, P \in \Omega_ {m} ^ {N}.\tag{4}
$$

A column-generation algorithm should be used to solve the linear relaxation of (1)–(4). However, even using large neighborhoods (or even enforcing that all routes are elementary) the resulting bounds are often not good enough for building an effective branchand-price algorithm. Therefore, the SPF should be reinforced by adding cuts. 

Let $C \subseteq V ^ { \prime }$ be a subset of the customers, and define $\begin{array} { r } { { \boldsymbol { w } } ( { \boldsymbol { C } } ) = \sum _ { v \in { \boldsymbol { C } } } { \boldsymbol { w _ { v } } } } \end{array}$ as its total demand. The value $\lceil w ( C ) / W \rceil$ is a valid lower bound on the number of vehicles that must visit C. Therefore, the following rounded capacity cut (RCC) (Laporte and Nobert 1983) is valid: 

$$
\sum_ {m \in M} \sum_ {P \in \Omega_ {m} ^ {N}} \left(\sum_ {a \in \delta^ {+} (C) \cup \delta^ {-} (C)} x _ {a} ^ {P}\right) \lambda_ {P} \geq 2 \lceil w (C) / W \rceil .\tag{5}
$$

RCCs are known to provide a significant reinforcement of the SPF on the CVRP (Fukasawa et al. 2006). However, they do not work so well on many HFVRP and VRPTW instances. This happens because the expression w C /W , which depends only on the largest vehicle capacity W and disregards time windows, can be a poor bound on the actual number of vehicles visiting C. The coefficient of a variable $\lambda _ { P }$ in a RCC is given by a linear expression over the values of $x _ { a } ^ { P } .$ . This means that RCCs are robust cuts (Pessoa, de Aragão, and Uchoa 2008); such cuts do not have any impact in the difficulty of the pricing subproblem. 

By using a Chvatal ´ –Gomory rounding of a subset $C \subseteq V ^ { \prime }$ of Constraint (2), relaxed to $\leq ,$ with multipliers $p _ { v } \ ( 0 < p _ { v } < 1 ) , \ v \in C ,$ , the following valid rank-1 cut (R1C) is obtained: 

$$
\sum_ {m \in M} \sum_ {P \in \Omega_ {m} ^ {N}} \left\lfloor \sum_ {v \in C} p _ {v} y _ {v} ^ {P} \right\rfloor \lambda_ {P} \leq \left\lfloor \sum_ {v \in C} p _ {v} \right\rfloor .\tag{6}
$$

The subset row cuts (SRCs) proposed in Jepsen et al (2008) are the particular R1Cs with multipliers $p _ { v } = 1 / K ,$ where $K$ is an integer, for all $v \in C .$ . Recently, the optimal multiplier vectors for R1Cs with up to five rows have been determined by Pecin et al. (2017c): 

$$
\bullet \text {   for   } | C | = 3, (\frac {1}{2}, \frac {1}{2}, \frac {1}{2});
$$

• f C 4, <sup>2</sup> , <sup>1</sup> , <sup>1</sup> , <sup>1</sup> and its permutations; 

1 1 1 1 <sup>,</sup> <sup>1</sup>4), $\begin{array} { r } { \big ( \frac { 3 } { 5 } , \frac { 2 } { 5 } , \frac { 2 } { 5 } , \frac { 1 } { 5 } , \frac { 1 } { 5 } \big ) , ~ \big ( \frac { 1 } { 2 } , \frac { 1 } { 2 } , \frac { 1 } { 2 } , \frac { 1 } { 2 } , \frac { 1 } { 2 } \big ) , ~ \big ( \frac { 2 } { 3 } , \frac { 2 } { 3 } , \frac { 2 } { 3 } , \frac { 1 } { 3 } , \frac { 1 } { 3 } \big ) , ~ \big ( \frac { 3 } { 4 } , \frac { 3 } { 4 } , \frac { 2 } { 4 } , \frac { 2 } { 4 } , \frac { 1 } { 4 } \big ) . } \end{array}$ , and their permutations. 

They are optimal in the sense that they generate R1Cs that are equivalent or dominate the $\mathrm { R 1 C s }$ generated with any other multipliers. When nonelementary routes can be generated, one can also apply cuts as in (6) with $C = \dot { \{ v \} } \mathrm { a n d } p _ { v } = 1 / 2$ for some $v \in V ^ { \prime }$ 

R1Cs are known to be very effective. However, because of the rounding down operator, the coefficient of a variable $\lambda _ { P }$ in a R1C is not given by a linear expression over the values of $x _ { a } ^ { P } ,$ , so those cuts are nonrobust 

The concept of limited memory for reducing the negative impact of $\mathrm { R 1 C s }$ in the pricing, first proposed in Pecin et al. (2014, 2017b), represented a breakthrough in the performance of BCP algorithms for VRP. In this paper, we assume that the generalized arc memory variant of the concept (Pecin et al. 2017a) is being used. Each R1C, indexed by $\ell ,$ is associated to a customer subset $C ^ { \ell } \subseteq V ^ { \prime } .$ , a multiplier vector $( p _ { v } ^ { \ell } ) _ { v \in C ^ { \ell } } ,$ and an arc memory set $A M ^ { \ell }$ with ${ \dot { A } } M ^ { \ell } \subseteq A$ . The idea is that, when a route $P \in \Omega _ { m } ^ { N }$ leaves the memory set, that ${ \mathrm { i } } \mathbf { s } ,$ follows an arc not in AM<sup></sup>, it “forgets” previous visits to nodes in $C ^ { \ell } ,$ , yielding a coefficient for $\lambda _ { P }$ in the cut  that may be smaller than the original coefficient $\begin{array} { r } { \big \lfloor \sum _ { v \in C ^ { \ell } } p _ { v } ^ { \ell } y _ { v } ^ { P } \big \rfloor } \end{array}$ . The memory set $A M ^ { \ell }$ is defined during the cut-separation procedure as a minimal set preserving the coefficients of the route variables $\lambda _ { P }$ that have a positive value in the current linear relaxation solution. Given $C \subseteq V ^ { \prime } .$ , a multiplier vector p of dimension C , and a memory arc set $A M \subseteq A$ , the limited-arc-memory R1C cut is defined as 

$$
\sum_ {m \in M} \sum_ {P \in \Omega_ {m} ^ {N}} \alpha (C, A M, p, P) \lambda_ {P} \leq \left\lfloor \sum_ {v \in C} p _ {v} \right\rfloor ,\tag{7}
$$

where the coefficient α $( C , A M , p , P )$ of the route variable $\lambda _ { P } , P \in \Omega _ { m } ^ { N }$ , is computed by the following pseudocode. 

Function $\alpha(C, AM, p, P = (v_0 = 0, v_1, \ldots, v_k, v_{k+1} = n + 1))$ $\alpha \leftarrow 0$ , $S \leftarrow 0$ for j = 1 to k do

if $(v_{j-1}, v_j) \notin AM$ then $S \leftarrow 0$ if $v_j \in C$ then $S \leftarrow S + p_{v_j}$ if $S \geq 1$ then $S \leftarrow S - 1$ , $\alpha \leftarrow \alpha + 1$ return $\alpha$ 

This function can be explained as follows. Whenever a route P visits a vertex $v \in C ,$ the multiplier $p _ { v }$ is added to the state variable 6. When ${ \mathcal { G } } \geq { 1 , }$ , 6 is decremented and α is incremented. If $A M = A$ , the function returns $\textstyle { \big \lfloor } \sum _ { v \in C } p _ { v } y _ { v } ^ { P } { \big \rfloor }$ and the limited-memory cut would be equivalent to the original cut. On the other hand, if ${ \hat { A M } } \subset A ,$ , it may happen that P uses an arc not in AM when $\mathcal { G } > 0 _ { \iota }$ , causing the state 6 to be reset to zero and $^ { \prime \prime } \mathrm { f o r g e t t i n g ^ { \prime \prime } }$ some previous visits to nodes in $C .$ In this case, the returned coefficient may be less than the original coefficient. However, memory sets can be “corrected” in the next separation round. The final linear relaxation bound obtainable with limited memory R1Cs is exactly that potentially achieved with ordinary R1Cs. 

## 2.3. Pricing Problem

We now define the pricing problem for the SPF over ng-routes and with additional RCCs and limitedmemory R1Cs, which decomposes into subproblems, one for each vehicle type. In the subproblem for type $m \in M$ , we search for a route $P \in \Omega _ { m } ^ { N }$ with the minimum reduced cost. Let $\mathcal { J }$ be the current set of active RCCs (5), and let + be the current set of active limitedmemory $\mathrm { R 1 C s ( 7 ) }$ . Each cut $j \in { \mathcal { F } }$ is determined by a set $C ^ { \eta }$ of customers. Each cut $\boldsymbol { \dot { \ell } } \in \mathcal { L }$ is determined by the triple $( C ^ { \ell } , A M ^ { \ell } , p ^ { \ell } )$ . Let $( \pi , \mu , \nu , \sigma )$ be the current dual solution of the linear relaxation of (1)–(4) with sets ) and + of cuts added, where π corresponds to the partitioning Constraint (2), $\mu$ corresponds to Constraint (3), ν corresponds to the set $\mathcal { J }$ of active ${ \mathrm { R C C s } } ,$ and $\sigma$ corresponds to the set + of active limitedmemory R1Cs. Let $\bar { c } ^ { m } ( \pi , \nu )$ be the vector of current arc reduced costs, where 

$$
\bar{c}^{m}_{(v,v^{\prime})}(\pi ,\nu) = c^{m}_{(v,v^{\prime})} - \frac{\pi_{v}}{2} -\frac{\pi_{v^{\prime}}}{2} -\sum_{\substack{j\in \mathcal{J}:\\ (v,v^{\prime})\in \delta^{+}(C^{\prime})\cup \delta^{-}(C^{\prime})}}\nu_{j},\tag{8}
$$

where $\pi _ { 0 }$ and $\pi _ { n + 1 }$ are defined as zero. The pricing subproblem $( \mathrm { P S P } ^ { m } )$ for vehicle type $m \in M$ is then formulated as 

$$
\min _ {P \in \Omega_ {m} ^ {N}} \left(\sum_ {a \in P} \bar {c} _ {a} ^ {m} (\pi , \nu) - \sum_ {\ell \in \mathscr {L}} \sigma_ {\ell} \cdot \alpha \big (C ^ {\ell}, A M ^ {\ell}, p ^ {\ell}, P \big) - \mu_ {m}\right).
$$

3. Bucket Graph–Based Labeling Algorithm 3.1. SPPRC over Forward and Backward Graphs We now formulate the pricing problem defined in Section 2.3 as a SPPRC in a forward graph $\vec { G } = ( V , \vec { A } )$ that is identical to $G ,$ , so ${ \vec { A } } = A$ . In this section we consider the dual solution $( \pi , \mu , \nu , \sigma )$ fixed and also drop index m for more clarity. Therefore, the reduced cost of an arc $a \in { \vec { A } }$ is denoted simply as $\bar { c } _ { a }$ . Define a set of two resources $R = \{ 1 , 2 \}$ corresponding to vehicle capacity and time. The capacity resource consumption of each arc $( v , v ^ { \prime } ) \in \vec { A }$ equals $w _ { v ^ { \prime } . }$ , and the time resource consumption of the same arc equals $s _ { v } + t _ { ( v , v ^ { \prime } ) }$ . For convenience, we use the same notation $d _ { \vec { a } , r }$ for the resource consumption of an arc $\vec { a } \in \vec { A }$ for both resources $r \in R$ . Each vertex $v \in V$ has lower and upper bounds $l _ { v , r }$ and $\boldsymbol { u } _ { v , r }$ on the accumulated resource consumption to v of each resource $r \in R .$ . For the second resource (time) these bounds correspond to original time windows. For the first (capacity) resource, we have $l _ { v , 1 } = 0$ and $u _ { v , 1 } = W$ . To simplify the notation, we often drop the resource index r to represent a vector with the corresponding values for all resources in R, e.g. $\boldsymbol { l } _ { v } = ( l _ { v , 1 } , \ldots , l _ { v , | R | } ) ^ { \top }$ . Moreover, we use $x \odot y$ to denote the componentwise product of two vectors x and y. We always represent a forward path $\vec { P }$ in graph $\vec { G }$ as defined by its arcs: $\scriptstyle { { \vec { P } } = }$ $( \vec { a } _ { 1 } ^ { \vec { P } } , \vec { a } _ { 2 } ^ { \vec { P } ^ { \mathbf { \alpha } } } , \vec { a } _ { | \vec { P } | } ^ { \vec { P } } )$ , with $\vec { a } _ { j } ^ { \vec { P } } { = } ( v _ { j - 1 } ^ { \vec { P } } , v _ { j } ^ { \vec { P } } ) , v _ { 0 } ^ { \vec { P } } { = } 0$ , and $v _ { | \vec { P } | } ^ { \vec { P } } = n + 1$ To simplify notation, we may drop the superscript $\vec { P }$ when it is clear from the context. Path ${ \vec { P } } _ { \mathbf { \Phi } }$ yields a vector of accumulated resource consumptions $q _ { j } ^ { \vec { P } } \in \mathbb { R } ^ { | R | }$ at vertices that can be computed as 

$$
q _ {j} ^ {\vec {P}} = \left\{ \begin{array}{l l} l _ {0} & \text { if } j = 0, \\ \max \bigl \{q _ {j - 1} ^ {\vec {P}} + d _ {\vec {a} _ {j}}   l _ {v _ {j}} \bigr \} & \text { otherwise }, \end{array} \right.\tag{9}
$$

where the max denotes the componentwise maximum function of two vectors. A forward path $\vec { P }$ is feasible if it respects 

• the resource consumption bounds: 

$$
q _ {j} ^ {\vec {P}} \leq u _ {v _ {j}}, \quad \forall j \in \left\{0, \dots , | \vec {P} | \right\};\tag{10}
$$

• and $n g \mathrm { - } { \sf p a }$ th neighborhoods: 

$$
\begin{array}{c} \forall (i, j) \in \left\{(i, j): 0 \leq i <   j \leq | \vec {P} |, v _ {i} = v _ {j} = v \right\} \\ \exists h: i <   h <   j, v \notin N _ {v _ {h}}. \end{array}\tag{11}
$$

Condition (11) can be rewritten in the following way. Let $\mathcal { F } _ { i } ^ { \vec { P } }$ be the set of vertices defined as $\mathcal { \bar { F } } _ { 0 } ^ { \vec { P } } = \emptyset$ and $\mathcal { F } _ { j } ^ { \vec { P } } { = } ( \mathcal { F } _ { j - 1 } ^ { \vec { P } } \cap N _ { v _ { j } } ) \cup \{ v _ { j } \}$ for all $1 \leq j \leq | \vec { P } |$ . Then (11) is equivalent to $v _ { j } \notin \mathcal { F } _ { j - 1 } ^ { \vec { P } }$ for all $1 \leq j \leq | \vec { P } |$ . Let $\bar { c } ^ { \vec { P } } ( \pi , \mu ,$ $\nu , \sigma )$ be the total reduced cost of path ${ \vec { P } } \colon$ 

$$
\begin{array}{l} \bar {c} ^ {\vec {P}} (\pi , \mu , \nu , \sigma) \\ = \sum_ {\vec {a} \in \vec {P}} \bar {c} _ {\vec {a}} (\pi , \nu) - \sum_ {\ell \in \mathscr {L}} \sigma_ {\ell} \cdot \alpha (C ^ {\ell}, A M ^ {\ell}, p ^ {\ell}, \vec {P}) - \mu . \end{array}
$$

Also let $\vec { \mathcal P }$ be the set of all feasible forward paths. The pricing subproblem then can be reformulated as finding a path in $\vec { \mathcal P }$ minimizing the total reduced cost: min ${ \vec { p } } _ { \in \mathcal { \vec { P } } } \vec { c } ^ { \vec { P } } ( \pi , \mu , \nu , \sigma )$ . In the remainder of this section, we use simplified notations $\bar { c } ^ { P }$ and $\overline { { c } } _ { a }$ as the dual solution $( \pi , \mu , \nu , \sigma )$ is fixed. 

We now define the backward graph $\stackrel {  } { G } = ( V , \stackrel {  } { A } )$ with source n 1 and sink 0. Set A contains one backward arc $\overleftarrow { a } = ( v ^ { \prime } , v )$ for each forward arc $\vec { a } = ( v , v ^ { \prime } ) \in \vec { A }$ . Each backward arc a has the same reduced cost $\bar { c } _ { \stackrel {  } { a } } = \bar { c } _ { \vec { a } }$ and same resource consumption $d _ {  } = d _ { \vec { a } }$ as the corresponding forward arc. 

A backward path $\dot { P }$ is denoted by $\stackrel {  } { P } = ( \stackrel {  } { a } _ { 1 } , \stackrel {  } { a } _ { 2 } , \ldots ,$ $ G _ { \mid  \mid } )$ in graph $\bar { G } , \mathsf { \Pi } _ { i _ { j } } ^ { \mathsf { ^ { * } } } = ( v _ { j - 1 } , v _ { j } ) , v _ { 0 } = n + 1 , v _ { | P | } = 0 ,$ , possibly containing cycles, where we may add the superscript P if necessary as in the forward graph. The accumulated consumption vector $q _ { j } ^ {  } \in \mathbb { R } ^ { | R | }$ in $\bar { P }$ is calculated recursively as 

$$
q _ {j} ^ {\tilde {p}} = \left\{ \begin{array}{l l} u _ {n + 1} & \text {if j = 0 ,} \\ \min \Bigl \{q _ {j - 1} ^ {\tilde {p}} - d _ {\tilde {u} _ {j}},   u _ {v _ {j}} \Bigr \} & \text {otherwise,} \end{array} \right.
$$

where the min denotes the componentwise minimum function of two vectors. Feasibility conditions for a backward path are the same as for a forward path except that (10) is replaced by $q _ { j } ^ { \bar { P } } \geq l _ { v _ { j } } ,$ , for all $j \in \{ 0 , \ldots , | \overleftarrow { P } | \}$ . We denote as $\mathbf { \sigma } _ { \mathcal { P } } ^ {  }$ the set of all feasible backward paths. Sets $\mathcal { F } _ { j } ^ {  } , 0 \leq j \leq | \overleftarrow { P } |$ , are defined in the same way as for a forward path. 

It is easy to see that a forward path $\vec { P }$ belongs to $\vec { \mathcal P }$ if and only if the corresponding backward path belongs to $\mathcal { \hat { P } } ,$ , where $| \vec { P } | = | \stackrel { \left. } { P } | , \stackrel { \right. } { a _ { j } } = ( v , v ^ { \prime } ) .$ , and $\overleftarrow { a } _ { | P | + 1 - j } = ( v ^ { \prime } , v ) , 1 \leq j \leq | \vec { P } |$ . Also, we have $q _ { j } ^ { \vec { P } } \leq q _ { | \bar { P } | - j } ^ { \stackrel {  } { P } }$ for $0 \leq j \leq | \vec { P } |$ . An example of a pair of forward and backward paths together with their consumption of one resource is given in Figure 1. Moreover, the total reduced costs of $\mathrm { \Sigma } _ { P } ^ {  }$ and $\vec { P }$ are equal, as coefficients $\alpha ( C , A M , p , P )$ do not change if we traverse path P in the backward order in function α. Therefore, the pricing problem defined in Section 2.3 can also be reformulated as finding a path in 3 minimizing the total reduced cost. 

From now on, we use accent for a forward entity, accent for a backward entity, and no accent for an entity which can be both forward or backward (when it is applied). We also use accent $_ { \circ } ^ {  }$ for an entity of the opposite sense. 

## 3.2. Labels and Dominance Rule

We devise a labeling algorithm for the previously defined SPPRC where each label ${ \cal L } = ( \bar { c } ^ { L } , v ^ { L } , q ^ { L } , \mathcal { F } ^ { L } , \mathcal { S } ^ { \bar { L } } )$ corresponds to a partial forward or backward path P (partial means that we may have $v _ { | P | } ^ { P } \notin \{ 0 , \dot { n } + 1 \} )$ where $\bar { c } ^ { L } = \bar { c } ^ { P } , \ v ^ { L } = v _ { | P | } ^ { P } , \ q ^ { L } = q _ { | P | } ^ { P } , \ \mathcal { F } ^ { L } = \overset { . } { \mathcal { F } } _ { | P | } ^ { P } ,$ , and $\mathcal { G } ^ { L } \in$ $\mathbb { R } ^ { | \mathcal { L } | }$ gives the current state (computed as in function α) of each cut $\ell \in \mathcal L$ for label L. 

A label L dominates label L if $v ^ { L } = v ^ { L ^ { \prime } } , \ q ^ { L } \leq q ^ { L ^ { \prime } }$ $( q ^ { L } \ge q ^ { L ^ { \prime } }$ for backward labels), $\mathcal { F } ^ { L } \subseteq \mathcal { F } ^ { L ^ { \prime } }$ , and 

$$
\bar {c} ^ {L} - \sum_ {\ell \in \mathcal {L}: \mathcal {S} _ {\ell} ^ {L} > \mathcal {S} _ {\ell} ^ {L ^ {\prime}}} \sigma_ {\ell} \leq \bar {c} ^ {L ^ {\prime}},\tag{12}
$$

as $\sigma _ { \ell } \le 0 , \ell \in \mathcal { L }$ (Jepsen et al. 2008). Condition $\overline { { c } } ^ { L } > \overline { { c } } ^ { L ^ { \prime } }$ is sufficient to verify that L does not dominate $L ^ { \prime } .$ 

Figure 1. An Example of a Forward and the Corresponding Backward Paths in the Case of One Resource 

![](images/a8464a606b867b32eebc4da67e12541d67f319051d1a05ec8989dc6de6cea128.jpg)


## 3.3. Bucket Graph

A critical aspect of labeling algorithms that solve the previously described SPPRC is when to perform dominance checks. Given a set of labels that potentially dominate each other, dominance checks may be performed for all pairs of labels. However, it can be prohibitively time consuming. Alternatively, skipping too many dominance checks may cause a premature explosion on the number of maintained labels. One approach to address this issue is to partition the labels into buckets and to ensure that no pair of labels inside a bucket dominate each other. Pecin et al. (2017b) followed this approach by defining buckets for every possible resource consumption of labels and skipping interbucket dominance checks. In this paper we define buckets differently in order to avoid resource discretization. Also, we perform interbucket dominance checks, but less frequently. 

In the proposed labeling algorithm, labels are grouped into buckets based on their final vertices and on ranges defined for both accumulated resource consumption values. Moreover, we define bucket arcs connecting pairs of buckets through which the labels can be extended. Different bucket graphs are then defined for forward and backward labeling. Bucket graphs are useful because they help to determine an efficient order of treatment for the buckets. If the bucket graph is acyclic, it is desirable to process the buckets in topological order because no further extension from a bucket is necessary after it has been processed. If the bucket graph contains cycles, then buckets are handled in the topological order of the graph’s strongly connected components, trying to minimize such reprocessing. Additionally, the bucket graph is used to avoid label extensions that are proved not to contribute to a solution that improves the current best one. This is achieved by removing arcs from the bucket graph based on a reduced cost argument. In what follows, we present all the notation required to formalize these ideas. 

The set of buckets associated to a given vertex $v \in V$ is determined by a step size $\tilde { \boldsymbol { d } } _ { \boldsymbol { r } }$ for each resource $r \in R$ Let $\mathcal { K } _ { v }$ be the set of all R -dimensional integer vectors such that the rth component belongs to $\{ 0 ,$ $1 , \dots , \lfloor ( u _ { v , r } - l _ { v , r } ) / \tilde { d } _ { r } \rfloor \} , r \in R .$ . A forward bucket b is defined by a pair of a vertex and lower bound vector $( \tilde { v } _ { \vec { b } } , \tilde { l } _ { \vec { b } } ) = ( \hat { v } , l _ { v } + \kappa \odot \tilde { d } ) , v \in V , \kappa \in \mathcal { K } _ { v } .$ A forward label L is contained in forward bucket b if $v ^ { \vec { L } } = \tilde { v } _ { \vec { k } }$ and $\tilde { l } _ { \vec { b } } \leq q ^ { \vec { L } } < \tilde { l } _ { \vec { b } } + \tilde { d }$ . Similarly, a backward bucket $\overleftarrow { b }$ is defined by a pair of a vertex and upper bound vector $( \tilde { v } _ { b } ^ {  } , \tilde { u } _ { b } ^ {  } ) = ( v , u _ { v } - \kappa \odot \tilde { d } ) , v \in V , \kappa \in \bar { \mathcal { K } } _ { v } .$ A backward label $\overleftarrow { L }$ is contained in backward bucket if $v ^ { \stackrel {  } { L } } = \tilde { v } _ { b } ^ {  }$ and $\tilde { u } _ { b } ^ { \scriptscriptstyle + } \ge q ^ { \stackrel {  } { L } } > \tilde { u } _ { b - \tilde { d } } ^ { \scriptscriptstyle + }$ . Let $b ^ { L }$ be the bucket containing label L. 

For a forward (backward) bucket $b ,$ , let $\kappa ^ { b }$ be the vector $\kappa \in \mathcal { K } _ { \tilde { v } _ { b } }$ such that $\tilde { l } _ { b } = l _ { \tilde { v } _ { b } } + \kappa \odot \tilde { d } \left( \tilde { u } _ { b } = u _ { \tilde { v } _ { b } } - \kappa \odot \tilde { d } \right)$ We say that bucket $b ^ { \prime }$ is componentwise smaller than bucket b (denoted as $b ^ { \prime } \prec b )$ if buckets are of the same sense, $\tilde { v } _ { b } , = \tilde { v } _ { b }$ , and $\kappa ^ { b ^ { \prime } } \leq \kappa ^ { b }$ 

Let $\vec { B } ^ { \enspace } ( \tilde { B } )$ be the set of all forward (backward) buckets. We define as $\vec { \Gamma } ( \stackrel {  } { \Gamma } )$ the following set of directed forward (backward) bucket arcs. 

Each forward/backward bucket arc $\gamma \in \Gamma$ is defined by a pair $( b _ { \gamma } , a _ { \gamma } )$ of a bucket and an arc of the corresponding sense, $b _ { \gamma } \in B , a _ { \gamma } \in A .$ , and $\tilde { v } _ { b _ { \nu } }$ is the tail of $a _ { \gamma } .$ The tail of a forward/backward bucket arc γ is bucket $b _ { \gamma }$ Set $\vec { \Gamma }$ contains γ if $\tilde { l } _ { \vec { b } _ { \vec { \gamma } } } + d _ { \vec { a } _ { \vec { \gamma } } } \leq u _ { v ^ { \prime } } ,$ , where $v ^ { \prime }$ is the head of $\vec { a } _ { \vec { \gamma } }$ . The head of a forward bucket arc $\vec { \gamma } \in \vec { \Gamma } ,$ denoted by $b _ { \vec { \gamma } } ^ { \mathrm { h e a d } }$ , is the bucket $\vec { b ^ { \prime } } \in \vec { B }$ such that $\tilde { v } _ { \vec { b ^ { \prime } } } = v ^ { \prime }$ and $\tilde { l } _ { \vec { b ^ { \prime } } } \leq \operatorname* { m a x } \{ \tilde { l } _ { \vec { b _ {  } } } + d _ { \vec { a _ {  } } } , l _ { v ^ { \prime } } \} < \tilde { l } _ { \vec { b ^ { \prime } } } + \tilde { d } .$ . Set $\stackrel {  } { \Gamma }$ contains $\{ \stackrel {  } { \gamma }$ if $\tilde { u } _ { b _ { \nu } ^ {  } } ^ {  } - d _ { a _ { \nu } ^ {  } } \geq l _ { v ^ { \prime } } ^ { ' }$ , where v is the head of $\smash { \overleftarrow { a } _ { \star } }$ . The head of a backward bucket arc $\{ \stackrel {  } { \gamma } \}$ , denoted by $b _ {  } ^ { \mathrm { h e a d } }$ , is the bucket $ b ^ { \prime } \in \mathsf { \bar { B } }$ such that $\tilde { v } _ { b ^ { \prime } } = v ^ { \prime }$ and $\tilde { u } _ { b ^ { \prime } } \geq$ min $\{ \tilde { u } _ { \tilde { b } _ { \nu } } ^ {  } - d _ { \tilde { a } _ { \nu } } ^ {  } , u _ { v ^ { \prime } } \} >$ $\tilde { u } _ { b ^ { \prime } } - \tilde { d }$ . Let $\vec { \mathcal { B } } = ( \vec { B } , \vec { \Gamma } )$ and $\vec { \mathcal { B } } = ( \stackrel {  } { B } , \stackrel {  } { \Gamma } )$ be the directed forward and backward bucket graphs, respectively. 

For each forward/backwardbucket $b \in B ,$ , we define the set $\Phi _ { b }$ of “adjacent” componentwise smaller buckets: 

$$
\begin{array}{r l} & {\Phi_ {b} = \Big \{b ^ {\prime}: \tilde {v} _ {b ^ {\prime}} = \tilde {v} _ {b}, \exists r ^ {\prime} \in R, \kappa_ {r ^ {\prime}} ^ {b ^ {\prime}} = \kappa_ {r ^ {\prime}} ^ {b} - 1, \kappa_ {r} ^ {b ^ {\prime}} = \kappa_ {r} ^ {b},} \\ & {\qquad \forall r \in R \setminus \{r ^ {\prime} \} \Big \}.} \end{array}
$$

As $| R | = 2 ,$ , we have $| \Phi _ { b } | \le 2$ . By definition, we have $\ b ^ { \prime } \prec b$ for all $b ^ { \prime } \in \Phi _ { b }$ . Let $\vec { \Gamma } ^ { \Phi } ( \overleftarrow { \Gamma } ^ { \Phi } )$ be the set of directed forward (backward) bucket arcs $( b ^ { \prime } , b )$ such that $b \in B ,$ $b ^ { \prime } \in \Phi _ { b }$ . Let $\vec { \mathcal { B } } ^ { \Phi } = ( \vec { B } , \phi ( \vec { \Gamma } ) \cup \vec { \Gamma ^ { \Phi } } )$ be the extended forward bucket graph, where $\phi ( \vec { \Gamma } ) = \{ ( b _ { \vec { \gamma } } , b _ { \vec { \nu } } ^ { \mathrm { h e a d } } ) : \vec { \gamma } \in \vec { \Gamma } \}$ Analogously, $\stackrel {  } { \mathcal { B } } ^ { \Phi } = ( \stackrel {  } { B } , \phi ( \stackrel {  } { \Gamma } ) \cup \stackrel {  } { \Gamma } ^ { \Phi } )$ is the extended backward bucket graph. Those extended graphs are not used in the core of the labeling algorithm, they are needed only in the initialization to compute their sets of strongly connected components and appropriate topological orders for them. Let $\boldsymbol { \mathcal { C } } _ { b }$ be the strongly connected component of a forward/backward bucket b 

A forward/backward label $L ,$ obtained by extension from label $L ^ { \prime }$ along a bucket arc γ with $b _ { \gamma } = b ^ { L ^ { \prime } }$ , is not necessarily contained in bucket $b _ { \nu } ^ { \mathrm { h e a d } }$ . For forward labels, this happens when $q _ { r } ^ { L ^ { \prime } } + d _ { a _ { \gamma } , r } \geq \tilde { l } _ { b _ { \nu } ^ { \mathrm { h e a d } } , r } + \tilde { d } _ { r }$ , for some $r \in R$ . The backward case is analogous. So, we may have $b _ { \nu } ^ { \mathrm { h e a d } } < b ^ { L }$ . Even in this case, we still consider that such an extension has been made along $\gamma .$ The bucket arcs in $\Gamma ^ { \Phi }$ ensure that there is always a path from $b ^ { L ^ { \prime } }$ to $b ^ { L }$ in the extended forward bucket graph. Therefore, $\mathcal { C } _ { b ^ { L } }$ cannot be before $\mathcal { C } _ { b ^ { L ^ { \prime } } }$ in the topological order of strongly connected components in ${ \mathrm { ~ \dot { F } ^ { \Phi } ~ } }$ 

For the forward graph depicted in Figure $^ { 2 , }$ the corresponding small extended bucket graph is shown in Figure 3. In this figure, each rectangle represents the space of possible resource consumption vectors for partial paths finishing in a corresponding vertex. The consumption of the first and second resources determines the shifts in the horizontal and vertical directions, respectively. Thus, each square is associated with two ranges for the consumption of both resources whose dimensions are determined by the bucket steps. Each node of the extended bucket graph is depicted inside the corresponding square. The bucket arcs that belong to $\vec { \Gamma }$ and $\vec { \Gamma } ^ { \Phi }$ correspond to arrows between rectangles and between squares of the same rectangle, respectively. Finally, an example of a strongly connected component of this extended bucket graph is printed in bold in the figure. 


Figure 2. An Example of Forward Graph


![](images/5336c2d9fba6c3b9fd4fe3f94bc3bb35c5bf44b300b8f0e4782198f2c4e17db6.jpg)


## 3.4. Monodirectional Labeling Algorithm

In this subsection, we describe the monodirectional labeling algorithm, which can be run in either a forward or backward sense to solve the pricing problem. An auxiliary function Extend $( L ^ { \prime } , \gamma , \bar { L } )$ presented below extends a label L to label L along a bucket arc $\gamma \in \Gamma .$ . It returns false if this extension is not possible. One can easily see that the steps performed by this function initialize the reduced cost and the final vertex of the new label, calculate its resource consumption and check the bounds, obtain its ng-route information and check its feasibility, and finally update its reduced cost considering the active R1Cs. 

Algorithm 1 (Monodirectional Labeling Algorithm)
if forward algorithm then $L^{init} \leftarrow (0,0,l_0,\emptyset,0)$ if backward algorithm then $L^{init} \leftarrow (0,n+1,u_{n+1},\emptyset,0)$ insert initial label $L^{init}$ to its bucket $b^{L^{init}}$ and mark $L^{init}$ as nonextended
foreach strongly connected component $\mathcal{C}$ in $\mathcal{B}^{\Phi}$ in a topological order do
repeat
    foreach bucket $b \in \mathcal{C}$ do
    foreach nonextended label $L': b^{L'} = b$ do
    if not DomInCompWiseSmallerBuckets ( $L', b^{L'}, \emptyset$ ) then
    foreach bucket arc $\gamma \in \Gamma$ such that $b_{\gamma} = b^{L'}$ do
    if Extend( $L', \gamma, L$ ) then
    if $L$ is not dominated by a label in $b^L$ then
    mark $L$ as nonextended and insert in $b^L$ remove labels dominated by $L$ from $b^L$ mark $L'$ as extended
until all labels in all buckets $b \in \mathcal{C}$ are extended
foreach bucket $b \in \mathcal{C}$ in a lexicographic order of $\kappa^b$ do $\bar{c}_b^{best} \leftarrow \min\{\min_{L: b^L=b}\{\bar{c}^L\}, \min_{b' \in \Phi_b}\{\bar{c}_{b'}^{best}\}\}$ return $P^{best} = \arg\min_{P^L: v^L=v'} \bar{c}^L$ , where $v'$ is the sink in G 

Function Extend $(L',\gamma ,L)$ $\bar{c}^L\gets \bar{c}^{L^{\prime}} + \bar{c}_{a_{\gamma}},v^{L}\gets$ the head of $a_{\gamma}$ $q^{L}\gets q^{L^{\prime}}$ if $L$ is a forward label then $\lfloor q^{L}\gets \max \{q^{L} + d_{a_{\gamma}},l_{v^{L}}\}$ $\lfloor$ if $q^{L} > u_{v^{L}}$ then return false  
if $L$ is a backward label then $\lfloor q^{L}\gets \min \{q^{L} - d_{a_{\gamma}},u_{v^{L}}\}$ $\lfloor$ if $q^{L} < l_{v^{L}}$ then return false  
if $v^{L}\in \mathcal{F}^{L^{\prime}}$ then return false $\mathcal{F}^L\gets (\mathcal{F}^{L^{\prime}}\cap N_{v^{L}})\cup \{v^{L}\}$ for $\ell \in \mathcal{L}$ do $\begin{array}{l}\textbf{if}a_{\gamma}\in AM^{\ell}\textbf{then}\mathcal{S}_{\ell}^{L}\gets \mathcal{S}_{\ell}^{L^{\prime}}\\ \textbf{else}\mathcal{S}_{\ell}^{L}\gets 0\\ \textbf{if}v^{L}\in C^{\ell}\textbf{then}\\ \left\lfloor \begin{array}{l}\mathcal{S}_{\ell}^{L}\gets \mathcal{S}_{\ell}^{L} + p_{v^{L}}^{\ell}\\ \textbf{if}\mathcal{S}_{\ell}^{L}\geq 1\textbf{then}\\ \lfloor \mathcal{S}_{\ell}^{L}\gets \mathcal{S}_{\ell}^{L} - 1,\bar{c}^{L}\gets \bar{c}^{L} - \sigma_{\ell} \end{array} \right.\\ \textbf{return true} \end{array}$ 

The labeling algorithm is presented in Algorithm 1. In this algorithm, we maintain values $\overline { { c } } _ { b } ^ { b e s t }$ equal to the smallest reduced cost of labels L such that $b ^ { L } \leq b .$ . It also uses the auxiliary function DomIn CompWiseSmallerBuckets $( L , b , \bar { B ^ { v i s i t e d } } )$ presented below, which checks whether a label L is dominated by a label contained in a bucket $b ^ { \prime }$ such that $b ^ { \prime } \preceq b ,$ $b ^ { \prime } \notin B ^ { v i s i t e d }$ . To avoid checking all componentwise smaller buckets, it assumes that the values of $\overline { { c } } _ { b } ^ { b e s t }$ are updated (for all buckets preceding $b ^ { L }$ in the topological order) and uses it to prune the search. If the search reaches a bucket $b ^ { \prime }$ such that $\bar { c } _ { b ^ { \prime } } ^ { b e s t } > \bar { c } ^ { L }$ , one can conclude that no label in $b ^ { \prime }$ or in the componentwise smaller buckets can dominate $L ,$ , and this search branch can be pruned. This function also receives the set $B ^ { v i s i t e d }$ of visited buckets needed to avoid processing the same bucket twice. For our case with one or two resources it is easy to do a specific implementation to verify whether a bucket belongs to set $B ^ { v i s i t e d }$ in a constant time. The same implementation can be used in functions ConcatenateLabel and UpdateBucketsSet described later. 


Figure 3. An Example of Extended Bucket Graph for the Forward Graph of Figure 2


![](images/9c7ca0c22c907a687dcf20988ead6745c6c6ab85a0b2d6736b002f734f2076ac.jpg)


Function DomInCompWiseSmallerBuckets (L, b, B $^{visited}$ ) $B^{visited} \leftarrow B^{visited} \cup \{b\}$ if $C_{b}$ precedes $C_{b^{L}}$ in the topological order used and $\bar{c}^{L} < \bar{c}_{b}^{best}$ then return false
if $b \neq b^{L}$ and L is dominated by a label in bucket b then return true
for $b' \in \Phi_{b} \setminus B^{visited}$ do
    if DomInCompWiseSmallerBuckets(L, b', B $^{visited}$ )
    then return true
return false 

Two buckets b and $b ^ { \prime }$ such that $b ^ { \prime } \in \Phi _ { b }$ may belong to the same strongly connected component. This may happen, for example, when b and $b ^ { \prime }$ are forward buckets and there is another forward bucket $b ^ { \prime \prime }$ with $\tilde { v } _ { b ^ { \prime \prime } } \ne \tilde { v } _ { b ^ { \prime } } = \tilde { v } _ { b }$ such that $\begin{array} { r } { \tilde { l } _ { b ^ { \prime } , r } < \tilde { l } _ { b ^ { \prime \prime } , r } < \tilde { l } _ { b , r } , } \end{array}$ for some $r \in R$ In that case, the extended bucket graph may contain the directed cycle $( ( b ^ { \prime } , b ) , ( b , b ^ { \prime \prime } ) , \breve { ( b ^ { \prime \prime } , \dot { b } ^ { \prime } ) } )$ . Therefore, values $\overline { c } _ { b } ^ { b e s t }$ for buckets in the same strongly connected component are calculated in a lexicographic order of $\kappa ^ { b }$ to ensure that $\overline { c } _ { b ^ { \prime } } ^ { b e s t }$ is set before $\overline { c } _ { b } ^ { b e s t }$ 

## 3.5. Bidirectional Labeling Algorithm

We call a pair of forward and backward labels $\vec { L }$ and $\stackrel {  } { L }$ ω-compatible if 

$$
v ^ {\vec {L}} \neq v ^ {\tilde {L}}, q ^ {\vec {L}} + d _ {(v ^ {\vec {L}}, v ^ {\tilde {L}})} \leq q ^ {\tilde {L}}, \mathscr {F} _ {\rho} ^ {\vec {L}} \cap \mathscr {F} _ {\rho} ^ {\tilde {L}} = \emptyset , \text { and } \bar {c} (P ^ {\vec {L}} \| P ^ {\tilde {L}}) <   \omega ,
$$

where $P ^ { \vec { L } } \| P ^ { \tilde { L } }$ is the path obtained by concatenation of partial paths $P ^ { \vec { L } }$ and $P _ { . } ^ { \tilde { L } }$ along arc $( v ^ { \vec { L } } , v ^ { \bar { L } } ) \in \vec { A } ,$ , and its total reduced cost $\bar { c } ( P ^ { \vec { L } } \| P ^ { \tilde { L } } )$ is calculated as 

$$
\bar{c}\Big(P^{\vec{L}}\| P^{\hat{L}}\Big) = \bar{c}^{\vec{L}} + \bar{c}_{\big(v^{\vec{L}},v^{\hat{L}}\big)} + \bar{c}^{\hat{L}} - \sum_{\substack{\ell \in \mathcal{L}:\\ \mathcal{S}_{\ell}^{\hat{L}} + \mathcal{S}_{\ell}^{\hat{L}}\geq 1}}\sigma_{\ell}.
$$

For a pair of forward and backward labels $\vec { L }$ and $\stackrel {  } { L }$ such that $v ^ { \vec { L } } \not = v _ { \textsc { - } } ^ {  } \bar { c } ^ { \vec { L } } + \bar { c } _ { ( v ^ { \vec { L } } , v ^ { \hat { L } } ) } + \bar { c } ^ { \overleftarrow { L } }$ is a lower bound on the value $\bar { c } ( P ^ { \vec { L } } \| P ^ { \overleftarrow { L } } )$ 

For the bidirectional labeling algorithm, we need to choose a resource $r ^ { * } \in R$ and a threshold value $q ^ { * } \in$ $[ l _ { 0 , r } * , u _ { n + 1 , r } * ]$ for it. The bidirectional labeling algorithm is presented in Algorithm 2. It uses the auxiliary procedure ConcatenateLabel $( \vec { L } , \overleftarrow { b } , P ^ { b e s t } , \overleftarrow { B } ^ { v a t t e d } )$ presented below, which tries to find a backward label $\stackrel {  } { L }$ such that $\acute { b } ^ {  } \acute { \Delta } b , \acute { b } ^ {  } \notin \stackrel {  } { B } ^ { v i s i t e d } .$ , and such that pair $( \vec { L } , \overleftarrow { L } )$ is $\bar { c } ^ { P ^ { b e s t } }$ -compatible, that is, concatenation of partial paths $P ^ { \vec { L } }$ and $P ^ { \stackrel {  } { L } }$ along arc $( v ^ { \vec { L } } , v ^ { \stackrel {  } { L } } )$ improves on $\mathbf { \hat { \Pi } } ^ { p b e s t }$ . We use values $\bar { c } ^ { b e s t }$ <sup>t</sup> as bounds to prune the search. From the reasoning of the previous paragraph, if $\bar { c } ^ { \vec { L } } + \bar { c } _ { ( v ^ { \vec { L } } , \tilde { v } _ { h } ^ { \epsilon } ) } + \bar { c } _ { \dot { b } } ^ { b e s t } \geq \bar { c } ^ { p ^ { b e s t } }$ , then such a label does not exist. All buckets that may contain extensions for $\vec { L }$ that improve on $P ^ { b e s t }$ are tried. In the literature, values $\bar { c } _ { \bar { b } } ^ { b e s t }$ are referred to as completion bounds (Christofides, Mingozzi, and Toth 1981). 

Algorithm 2 (Bidirectional Labeling Algorithm)
run forward labeling algorithm where only labels $\vec{L}$ , $q_{r^*}^{\vec{L}} \leq q^*$ are kept
run backward labeling algorithm where only labels $\stackrel{\leftarrow}{\vec{L}}, q_{r^*}^{\vec{L}} > q^*$ , are kept
let $P^{best}$ be the best complete path obtained in the two algorithms above
foreach forward label $\vec{L}$ do
    foreach bucket arc $\vec{\gamma} \in \vec{\Gamma}$ such that $\vec{b}_{\vec{\gamma}} = \vec{b}^{\vec{L}}$ do
    if Extend( $\vec{L}, \vec{\gamma}, \vec{L'}$ ) and $q_{r^*}^{\vec{L'}} > q^*$ then
    let $\stackrel{\leftarrow}{\vec{b}} \in \stackrel{\leftarrow}{\vec{B}}$ be the bucket such that $\tilde{u}_{\vec{b}} \geq q^{\vec{L'}} > \tilde{u}_{\vec{b}} - \tilde{d}$ ConcatenateLabel( $\vec{L}, \vec{b}, P^{best}, \emptyset$ )
return $P^{best}$ Procedure ConcatenateLabel( $\vec{L}, \vec{b}, P^{best}, \stackrel{\leftarrow}{B}^{visited}$ ) $\stackrel{\leftarrow}{B}^{visited} \leftarrow \stackrel{\leftarrow}{B}^{visited} \cup \{\stackrel{\leftarrow}{b}\}$ if $\bar{c}_{\vec{L}} + \bar{c}_{(v^{\vec{L}}, \tilde{v}^{\vec{b}})} + \bar{c}_{\vec{b}}^{best} \geq \bar{c}^{P^{best}}$ then return
foreach label $\stackrel{\leftarrow}{L}: b_{\vec{L}} = \stackrel{\leftarrow}{b}$ do
    if pair ( $\vec{L}, \vec{L}$ ) is $\bar{c}^{P^{best}}$ -compatible then $P^{best} \leftarrow (P_{\vec{L}}, \vec{a} = (v^{\vec{L}}, v^{\vec{L}}), P_{\vec{L}})$ foreach $b' \in \Phi_{\vec{b}} \setminus \stackrel{\leftarrow}{B}^{visited}$ do
    ConcatenateLabel( $\vec{L}, b', P^{best}, \stackrel{\leftarrow}{B}^{visited}$ )
return 

## 3.6. Symmetric Case

In this section, we suppose that (1) all time windows are the same and (2) the R1C arc memories are symmetric: $( v , v ^ { \prime } ) \in A M ^ { \ell }$ if and only if $( v ^ { \prime } , v ) \in A M ^ { \ell }$ for all $\ell \in \mathcal L$ . We now show that in this case the forward/ backward route symmetry can be exploited. 

First, we redefine the resource consumption of arcs. We make the capacity resource consumption of each arc $( v , v ^ { \prime } ) \in { \vec { A } }$ equal to $\begin{array} { r } { \frac { 1 } { 7 } \boldsymbol { w } _ { v } + \frac { 1 } { 7 } \boldsymbol { w } _ { v ^ { \prime } } , } \end{array}$ and we make the time resource consumption of this arc equal to $\frac { 1 } { 2 } s _ { v } + t _ { ( v , v ^ { \prime } ) } +$ $\scriptstyle { \frac { 1 } { 2 } } S _ { v ^ { \prime } }$ . Thus, the cost and the resource consumption of two arcs $( v , v ^ { \prime } )$ and $( v ^ { \prime } , v ) , v , v ^ { \prime } \in V .$ , become the same. Moreover, as for any route P coefficients $x _ { ( v , v ^ { \prime } ) } ^ { P }$ and $x _ { ( v ^ { \prime } , v ) } ^ { P }$ are the same in Constraint (2) and cut (5), and the reduced costs of these arcs are the same: 

$$
\bar {c} _ {(v, v ^ {\prime})} = \bar {c} _ {(v ^ {\prime}, v)}, \quad \forall v, v ^ {\prime} \in V.\tag{13}
$$

Consider now a backward label $\stackrel {  } { L }$ and the corresponding partial path $\overset {  } { P } = \overset {  } { P } _ { . } ^ {  }$ . Let $\vec { P }$ be the partial forward path such that $v _ { j } ^ { \vec { P } } = v _ { j } ^ { \tilde { P } } , 1 \leq j \leq | \tilde { P } | .$ , and let L be the forward label such that $\vec { P } ^ { \vec { L } } = \vec { P } .$ From (13) and from the fact that the R1C arc memories are symmetric, we have $\bar { c } ^ { \vec { L } } = \bar { c } ^ { \tilde { L } } , \mathcal { F } ^ { \vec { L } } = \mathcal { F } ^ { \tilde { L } }$ , and $\mathcal { S } ^ { \vec { L } } = \mathcal { G } ^ { \overleftarrow { L } }$ . As time windows are the same, resource bounds are also the same: $[ l _ { v } , u _ { v } ] = [ l , u ]$ for all $v \in V$ . Then, $q ^ { \vec { L } } - l = u - q ^ { \stackrel {  } { L } }$ Moreover, for every backward bucket ${ \bar { b } } ,$ , there exists a symmetric forward bucket $\vec { b }$ such that $\tilde { l } _ { \vec { b } } - l = u - \tilde { u } _ { \tilde { b } } ^ { }$ and $\boldsymbol { \kappa } ^ { \vec { b } } = \boldsymbol { \kappa } ^ { \overleftarrow { b } }$ 

In this case (which we call symmetric), in the bidi rectional labeling algorithm, we set $\begin{array} { r } { q ^ { * } = \frac { 1 } { 2 } l _ { 0 , r ^ { * } } + \frac { 1 } { 2 } u _ { n + 1 , r ^ { * } } , } \end{array}$ skip the backward labeling step, and use symmetric forward buckets and labels instead of backward buckets and labels in the concatenation step. 

## 4. Bucket Arc Elimination

The arc elimination procedure from Irnich et al. (2010) can be employed to remove arcs from the original graph using the argument that any path using one of these arcs is not contributing to an optimal solution. In this section, we generalize this procedure to eliminate bucket arcs from the bucket graph using the same argument. However, this generalization is not straightforward because of the dominance between labels. Even labels from the same bucket may use different bucket arcs when extended through the same path in the original graph. Hence, the same path extension may be feasible for the dominated label and not feasible for the dominating label due to the removal of some bucket arcs. We introduce below the new concept of jump bucket arcs to bring back compatibility between bucket arc elimination and dominance. 

We call a path $P \in \Omega _ { m } , \ m \in M ,$ , improving if $\lambda _ { P }$ participates in an integer solution of the original problem (SPF) with a cost smaller than the cost of the best solution found so far. We denote the latter cost as UB, as it is an upper bound on the optimal solution value of (SPF). Let $\mathbf { \hat { \Omega } } _ { \mathbf { \hat { \Omega } } } ^ { I }$ be the set of improving paths in $\Omega _ { m } .$ . We can exclude paths proved to be nonimproving from the solution space of the pricing problem. Note that if there are no improving paths (i.e., the best known solution is optimal) any path can be correctly excluded, so we only need to be careful in the case where some $\Omega _ { m } ^ { I }$ sets are not empty. So, we assume that case in the remainder of the section. 

Suppose that some variables $\lambda _ { P } , P \notin \Omega _ { m } ^ { I } ,$ , are fixed to zero in (SPF). Let $( \bar { \pi } , \bar { \mu } , \bar { \nu } , \bar { \sigma } )$ be the dual solution of the current restricted linear relaxation of (SPF), corresponding to Constraints (2) and (3) and to constraints of type (5) and (7). Let $z ^ { ( \mathrm { R S P F } ) } ( \bar { \pi } , \bar { \mu } , \bar { \nu } , \bar { \sigma } )$ be the value of this solution. The value 

$$
\begin{array}{l} L B ^ {\text {Lagr}} = z ^ {(\text {RSPF})} \big (\bar {\pi}, \bar {\mu}, \bar {\nu}, \bar {\sigma} \big) \\ \qquad + \sum_ {m \in M} \min \bigg \{0, \min _ {P \in \Omega_ {m} ^ {I}} \bar {c} ^ {P} \big (\bar {\pi}, \bar {\mu}, \bar {\nu}, \bar {\sigma} \big) \bigg \} \cdot U _ {m} \end{array}
$$

is a Lagrangian lower bound on the optimal solution value of SPF . It is obtained by dualizing Constraints $( 2 ) , ( 5 )$ , and $( 7 )$ with multipliers π¯ , ν¯, and σ¯ . For any vector $( z ^ { 1 } , z ^ { 2 } , \dots , z ^ { | M | } )$ such that 

$$
z ^ {m} \leq \min _ {P \in \Omega_ {m} ^ {I}} \bar {c} ^ {P} \big (\bar {\pi}, \bar {\mu}, \bar {\nu}, \bar {\sigma} \big), \quad m \in M,\tag{14}
$$

we have 

$$
L B = z ^ {(\mathrm{RSPF})} \big (\bar {\pi}, \bar {\mu}, \bar {\nu}, \bar {\sigma} \big) + \sum_ {m \in M} \min \{0, z ^ {m} \} \cdot U _ {m} \leq L B ^ {\mathrm{Lagr}}.\tag{15}
$$

Thus, LB is a valid lower bound on the optimum solution of (SPF). If an algorithm for solving pricing subproblem (PSP<sup>m</sup>) returns value $z ^ { m }$ satisfying Condition (14), we say that this algorithm satisfies (14). Such an algorithm can be used to calculate a valid lower bound for (SPF) even if it does not necessarily solve (PSP<sup>m</sup>) to optimality. In the remainder of this section, we again drop index m and consider the dual solution $( \bar { \pi } , \bar { \mu } , \bar { \nu } , \bar { \sigma } )$ fixed for more clarity. 

We say that a feasible forward/backward path $P \in$ ${ \mathcal P }$ passes through bucket arc $\gamma \in \Gamma$ if there is an index $j ,$ $1 \leq j \leq | P | .$ , such that $a _ { j } ^ { P } = a _ { \gamma } ,$ , and $\tilde { l } _ { b _ { \gamma } } \leq q _ { j - 1 } ^ { P } < \tilde { l } _ { b _ { \gamma } } + \tilde { d }$ for a forward path $( \tilde { u } _ { b _ { \gamma } } \geq q _ { j - 1 } ^ { P } > \tilde { u } _ { b _ { \gamma } } - \tilde { d }$ for a backward path). We denote such a bucket arc as $\gamma _ { j } ^ { P } .$ 

We call a forward/backward $\gamma \in \Gamma$ an improving bucket arc if there exists an improving path of the same sense passing through it. The bucket arc elimination procedure described in Section 4.2 reduces the current set Γ by removing some nonimproving bucket arcs from it. However, the labeling algorithm proposed in Section $^ { 3 , }$ in which labels are only extended over a reduced set Γ of bucket arcs does not necessarily satisfy Condition (14). 

Figure 4 illustrates a case where the value z of the best solution found by the labeling algorithm does not satisfy (14). In this figure, the value of a single resource consumption $r = 1$ for each partial path is represented by the level of its end node in the vertical axis. Respectively, $P$ and $P ^ { \prime }$ are improving and nonimproving forward paths. Thus, for each traversed arc, the corresponding bucket arc that is passed through depends solely on the total resources consumed up to this point. Paths $P$ and $P ^ { \prime }$ traverse different arcs until vertex $v _ { i - 1 } ^ { P } = v _ { i ^ { \prime } - 1 } ^ { P ^ { \prime } }$ and finish with the same subpaths from this point until the sink vertex (but with such subpaths passing through different bucket arcs). 

Assume that the reduced bucket graph does not contain the nonimproving bucket arc $\gamma$ corresponding to $( v _ { i ^ { \prime } - 1 } ^ { P ^ { \prime } } , v _ { i ^ { \prime } } ^ { P ^ { \prime } } )$ . Suppose also that $P ^ { \prime }$ has a reduced cost smaller than that of $P ,$ allowing the label L associated to $P ^ { \prime }$ until vertex $v _ { i ^ { \prime } - } ^ { P ^ { \prime } }$ to dominate the label L<sup>¯</sup> associated to $P$ until the same vertex. The reasoning for such domination is that one can always replace the partial path of L<sup>¯</sup> by that of $L ,$ causing $P$ to be replaced by $P ^ { \prime } ,$ , which has a smaller reduced cost. However, the absence of bucket arc $\gamma$ in the reduced bucket graph prevents $P ^ { \prime }$ from being found by the labeling algorithm. Thus, neither $P$ nor $P ^ { \prime }$ are obtained by the labeling algorithm, and the value z found by the algorithm may violate Condition (14). 


Figure 4. Illustration of the Proof of Proposition 1


![](images/7569d3df068bb62e6014e364a73b9ecd8281a770192c0e192ff61614285bd158.jpg)


The remainder of this section is divided in two parts. In Section 4.1, we show how to modify our labeling algorithm so that it satisfies Condition (14) when executed over a superset of all improving bucket arcs. In Section 4.2, we present a procedure for finding nonimproving bucket arcs and show the correcteness of its combination with the modified labeling algorithm. 

## 4.1. Modified Labeling Algorithm

To guarantee the fulfillment of Condition (14), labels in our forward (backward) labeling algorithm should not only be extended along the current reduced set $\vec { \Gamma } \ \mathrm { ( \bar { I } ) }$ of bucket arcs, but also along the set Ψ (Ψ) of jump bucket arcs obtained as shown below. Jump bucket arcs impose an additional resource consumption when traversed. Each forward/backward jump bucket arc $\psi \in \Psi$ is characterized by a triple $( b _ { \psi } ^ { \mathrm { b a s } } \hat { \mathbf { e } }$ $b _ { \psi } ^ { \mathrm { j u m p } } , a _ { \psi } )$ , where $b _ { \psi } ^ { \mathrm { b a s e } }$ is its base bucket, $b _ { \psi } ^ { \mathrm { j u m p } }$ is its jump bucket such that $\tilde { v } _ { b _ { \nu } ^ { \mathrm { b a s e } } } = \tilde { v } _ { b _ { \nu , \ast } ^ { \mathrm { j u m p } } } = \tilde { v } , b _ { \psi } ^ { \mathrm { b a \dot { s e } } } < b _ { \psi } ^ { \mathrm { j u m p } } .$ and $a _ { \psi } = ( \widetilde { v } , v ^ { \prime } ) \in A$ <sup>ψ ψ</sup>is its arc of the corresponding sense. In the labeling algorithm, each label L should be extended along a jump bucket arc $\psi \in \Psi \mathrm { i f } b ^ { L } = b _ { \psi } ^ { \mathrm { b a s e } }$ When extending label $L ^ { \prime }$ along $\psi \in \Psi ,$ , we first do the “jump,” that is, in the second line of function Extend $( L ^ { \prime } ,$ $\psi , L )$ , instead of $q ^ { L }  q ^ { L ^ { \prime } }$ , we do $q ^ { L } \gets \operatorname* { m a x } \{ q ^ { L ^ { \prime } } , \tilde { l } _ { b _ { \ast , \ast } ^ { \mathrm { j u m p } } } \}$ $( q ^ { L } \gets \operatorname* { m i n } \{ q ^ { L ^ { \prime } } , \tilde { u } _ { { b } _ { \iota , \cdot } ^ { \mathrm { j u m p } } } \}$ for a backward label). Function ψ ObtainJumpBucketArcs(Γ) presented below obtains the current set $\Psi$ of jump bucket arcs given the current reduced set Γ of bucket arcs. This function can be used both forward and backward. 

```txt
Function ObtainJumpBucketArcs(Γ)
Ψ ← ∅
foreach bucket b ∈ B do
    foreach a = (v, v') ∈ A such that v = ̃v_b do
    if ∉ γ ∈ Γ such that b_γ = b, a_γ = a then
    B̄ ← {b' > b : ∃γ ∈ Γ : b_γ = b', a_γ = a}
    remove from B̄ all buckets which are not componentwise minimal
    foreach b' ∈ B̄ do
    Ψ ← Ψ ∪ {ψ}, where b^base = b,
    b^jump = b', a_ψ = a
return Ψ 
```

We denote as $L _ { j } ^ { P }$ the label corresponding to the partial path consisting of the first j arcs of path P. If P passes through $\gamma ,$ , then there is an index j, $\begin{array} { r } { 1 \leq j \leq | P | , } \end{array}$ such that label $L _ { i } ^ { P }$ is contained in bucket $b _ { \gamma }$ . Let L Γ, Ψ and $\overleftarrow { \mathbf { L } } ( \Gamma , \Psi )$ be the sets of nondominated labels generated by the modified forward and backward monodirectional labeling algorithms, in which each label L is extended along all bucket arcs $\gamma \in \Gamma$ such that $b _ { \nu } = b ^ { L }$ and along alljump bucket arcs $\psi \in \Psi$ such that $b _ { \psi } ^ { \mathrm { b a s e } } = b ^ { L }$ 

The next proposition is auxiliary but very important. It shows that, for every improving path $P ,$ the modified monodirectional labeling algorithm either generates P or a path dominating P. Moreover, this is true for any prefix of P. This proposition applies both forward and backward. Its proof is illustrated in Figure 4. 

Proposition 1. Let the set Γ be a superset of all improving bucket arcs, and let Ψ be the set of jump bucket arcs computed using procedure ObtainJumpBucketArcs Γ . Then, for each improving path $P \in { \mathcal { P } }$ and for each index $j ,$ $0 \leq j \leq | P | ,$ , either $\stackrel { \triangledown } { L } _ { i } ^ { P } \in \mathbf { L }$ or there exists a label $L \in \mathbf { L }$ dominating $L _ { j } ^ { P } .$ , where $\mathbf { L } = \mathbf { L } ( \Gamma , \Psi )$ 

Proof. Consider an improving path $P \in { \mathcal { P } }$ . We prove this proposition by induction on the value of the index $j ,$ $0 \leq \hat { j } \leq | \hat { P } |$ . For $j = 0$ the proposition is true since there is a single label $L ^ { i n i t }$ in the bucket $b ^ { L ^ { i n i t } }$ that corresponds to vertex $v _ { 0 } ^ { P }$ . Now, assume that the proposition is true for $j = i - \check { 1 } , 1 \leq i \leq | P |$ . We must prove that it is also valid for $j = i .$ By the inductive hypothesis, either $L _ { i - 1 } ^ { P } \in$ L or there exists a label $L \in \mathbf { L }$ dominating $L _ { i - 1 } ^ { P }$ . Let us use the notation L defined for the latter case to also denote $L _ { i - 1 } ^ { P }$ in the former case. In all cases, $b ^ { L } \preceq b ^ { L _ { i - } ^ { P } }$ 1 . Let $\gamma ^ { \prime }$ and γ be the bucket arcs such that $b _ { \gamma ^ { \prime } } = b ^ { L } , b _ { \gamma } = b ^ { L _ { i - 1 } ^ { P } } ,$ and $a _ { \gamma ^ { \prime } } = a _ { \gamma } = a _ { i } ^ { P }$ . If $\gamma ^ { \prime } \in \Gamma ,$ the label $L ^ { \prime }$ obtained by extending L through $a _ { \gamma ^ { \prime } }$ dominates $L _ { i } ^ { P }$ . In this case, either $L ^ { \prime } \in \mathbf { L }$ or L is dominated by some label from $\scriptstyle \mathbf { L } ,$ which should also dominate $L _ { i } ^ { P }$ . In both situations, the proof is finished. Otherwise, since $\gamma ^ { \prime } \notin \Gamma , \gamma$ is an improving arc, and $b ^ { L } < b _ { \nu }$ , there exists a jump bucket arc $\psi \in \Psi$ such that $a _ { \psi } = a _ { i } ^ { \dot { P } } , b _ { \psi } ^ { \mathrm { b a s e } } = b ^ { L }$ , and $b _ { \psi } ^ { \mathsf { i u m p } } \preceq b ^ { L _ { i } ^ { P } }$ . The existence of such a jump bucket arcs is guaranteed by the definition of procedure ObtainJumpBucketArcs Γ . 

Thus, extending L through $a _ { \psi }$ leads to a label $L ^ { \prime \prime }$ that dominates $L _ { i } ^ { P }$ . As before, either $L ^ { \prime \prime } \in \mathbf { L }$ or $L ^ { \prime \prime }$ is dominated by some label from $\scriptstyle \mathbf { L } ,$ which dominates $L _ { i } ^ { P } ,$ finishing the proof. □ 

We have just shown essentially that the modified monodirectional labeling algorithm satisfies Condition (14). In Proposition $^ { 3 , }$ we show that the same condition is satisfied by the modified bidirectional labeling algorithm. First observe that any complete path can be divided into forward and backward parts by removing any arc it passes through. Moreover, the total reduced cost of the concatenation of these parts is equal to the reduced cost of the complete path. 

Observation 1. For a pair of corresponding forward/ backward path P and the opposite sense path $\smash { \overleftrightarrow { P } }$ and each value $j , 0 \leq j < | P | ,$ , the pair of labels $L _ { j } ^ { P }$ and $\stackrel {  } { L } _ { | P | - j - 1 } ^ { P }$ is ω-compatible for all $\omega > \overline { { c } } ^ { P }$ 

Secondly, we will need the following auxiliary proposition. 

Proposition 2. Suppose that (i) forward and backward labels $\vec { L ^ { \prime } }$ and $\stackrel {  } { L } { } ^ { \prime }$ are ω-compatible, (ii) either $\vec { L } = \vec { L ^ { \prime } }$ or label $\vec { L }$ dominates $\vec { L ^ { \prime } } ,$ , and (iii) either $\stackrel {  } { L } = \stackrel {  } { L } ^ { \prime }$ or label L dominates $\stackrel {  } { L } { } ^ { \prime }$ Then labels $\vec { L }$ and L are also ω-compatible. 

Proof. Let $a = ( v ^ { \vec { L } } , v ^ { \overleftarrow { L } } ) = ( v ^ { \vec { L ^ { \prime } } } , v ^ { \overleftarrow { L ^ { \prime } } } )$ . From the definition of dominance in Section 3.2 and the definition of ω-compatible labels in Section 3.5, we have $v ^ { \vec { L } } =$ $v ^ { \vec { L ^ { \prime } } } \not = v ^ { \hat { \bar { L } } ^ { \prime } } = v ^ { \hat { \bar { L } } } , q ^ { \vec { L } } + d _ { a } \leq q ^ { \vec { L ^ { \prime } } } + d _ { a } \leq q ^ { \overleftarrow { L ^ { \prime } } } \leq q ^ { \overleftarrow { L } }$ . We also have $\mathcal { F } ^ { \vec { L } } \subseteq \mathcal { F } ^ { \vec { L ^ { \prime } } } , \mathcal { F } ^ { \tilde { L } } \subseteq \mathcal { F } ^ { \tilde { L ^ { \prime } } }$ , and $\mathcal { F } ^ { \vec { L ^ { \prime } } } \cap \mathcal { F } ^ { \tilde { L ^ { \prime } } } = \emptyset$ . It follows then that $\mathcal { F } ^ { \vec { L } } \cap \mathcal { F } ^ { \stackrel {  } { L } } = \emptyset$ . From (12) and the definition of $\bar { c } ( P ^ { \vec { L } } \| P ^ { \{ }  )$ it follows that 

$$
\begin{array}{l}\bar{c}\bigg(P^{\vec{L}}||P^{\hat{L}}\bigg) = \bar{c}^{\vec{L}} + \bar{c}_{a} + \bar{c}^{\hat{L}} - \sum_{\substack{\ell \in \mathcal{L}:\\ \mathcal{S}_{\ell}^{\vec{L}} + \mathcal{S}_{\ell}^{\hat{L}}\geq 1}}\sigma_{\ell}\\ \leq \underbrace{\bar{c}^{\vec{L}^{\prime}} + \sum_{\ell \in \mathcal{L}:}\sigma_{\ell} + \bar{c}_{a} + \bar{c}^{\hat{L}^{\prime}} + \sum_{\ell \in \mathcal{L}:}\sigma_{\ell} - \sum_{\ell \in \mathcal{L}:}\sigma_{\ell}}_{\substack{\mathcal{S}_{\ell}^{\vec{L}} > \mathcal{S}_{\ell}^{\hat{L}^{\prime}}\\ \geq \bar{c}^{\vec{L}}\text{as}\vec{L}\text{dominates}\vec{L}^{\prime}}} \underbrace{\underbrace{\mathcal{S}_{\ell}^{\hat{L}} > \mathcal{S}_{\ell}^{\tilde{L}^{\prime}}}_{\geq \bar{c}^{L}\text{as}\check{L}\text{dominates}\check{L}^{\prime}}}_{\substack{\mathcal{S}_{\ell}^{\hat{L}} + \mathcal{S}_{\ell}^{\hat{L}^{\prime}}\geq 1\\ }}\\ = \bar{c}^{\vec{L}^{\prime}} + \bar{c}_{a} + \bar{c}^{\hat{L}^{\prime}} - \sum_{\ell \in \mathcal{L}:}\sigma_{\ell} + \sum_{\ell \in \mathcal{L}:}\sigma_{\ell}\\ \underbrace{\underbrace{\mathcal{S}_{\ell}^{\vec{L}^{\prime}} + \mathcal{S}_{\ell}^{\tilde{L}^{\prime}}\geq 1}_{= \bar{c}\left(P^{\vec{L}^{\prime}}\| P^{L^{\prime}}\right) <   \omega}\underbrace{\mathcal{S}_{\ell}^{\hat{L}^{\prime}} + \mathcal{S}_{\ell}^{\hat{L}^{\prime}}\geq 1}_{\leq 0\text{as}\sigma\leq 0}}\\ +\sum_{\substack{\ell \in \mathcal{L}:\\ \mathcal{S}_{\ell}^{\vec{L}} > \mathcal{S}_{\ell}^{\vec{L}^{\prime}}}}\sigma_{\ell} + \sum_{\ell \in \mathcal{L}:}\sigma_{\ell} - \sum_{\ell \in \mathcal{L}:}\sigma_{\ell}.\\ \underbrace{\underbrace{\mathcal{S}_{\ell}^{\vec{L}^{\prime}} + \mathcal{S}_{\ell}^{\hat{L}^{\prime}}\geq 1}_{\leq 0\text{as}\sigma\leq 0}}_{\leq 0\text{as}\sigma \leq 0}\end{array}
$$

It follows then that $\bar { c } ( P ^ { \vec { L } } | | P ^ { \tilde { L } } ) < \omega$ . Hence, all conditions for $\vec { L }$ and $\stackrel {  } { L }$ to be ω-compatible are met. □ 

We define as $z ( \vec { \Gamma } , \stackrel {  } { \Gamma } , \vec { \Psi } , \stackrel {  } { \Psi } )$ the reduced cost of the best path obtained by the modified bidirectional labeling algorithm in which forward labels are extended along bucket $\mathsf { a r c s }$ in $\vec { \Gamma }$ and $\vec { \Psi } ,$ and backward labels are extended along bucket arcs in Γ and $\smash { \mathrm { \bar { \Psi } } _ { \perp , \perp } ^ {  } }$ 

Proposition 3. Let the sets $\vec { \Gamma }$ and $\mathrm { \Sigma _ { \Gamma } ^ {  } }$ be supersets of all improving forward and backward bucket arcs, and let $\vec { \Psi }$ and $\mathrm { { \stackrel {  } { \Psi } } }$ be the sets of forward and backward jump bucket arcs computed using procedure ObtainJumpBucketArcs Γ . Then $\dot { z } \le c ^ { \vec { P } }$ for any improving path $\dot { \vec { P } } \in \dot { \vec { \mathcal { P } } }$ , where $z = z ( \vec { \Gamma } , \stackrel {  } { \Gamma } , \vec { \Psi } , \stackrel {  } { \Psi } )$ 

Proof. Consider an arbitrary improving path $\vec { P } \in \vec { \mathcal { P } }$ Let $j , 0 \le j \le | \vec { P } |$ be the largest index such that $\begin{array} { r } { q _ { r ^ { * } } ^ { \vec { L } _ { j } ^ { \vec { P } } } \leq q ^ { * } } \end{array}$ We divide the proof into two cases: $j = \left| \vec { P } \right|$ and $j < | \vec { P } |$ 

For $j = | \vec { P } | ,$ , let $\vec { L } = \vec { L } _ { j } ^ { \vec { P } }$ . We have $v ^ { \vec { L } } = n + 1$ . As $\vec { P }$ is improving, by Proposition 1, either $\vec { L } \in \vec { \bf L } ( \vec { \Gamma } , \vec { \Psi } )$ or there is a label $\vec { L ^ { \prime } } \in \vec { \mathbf { L } } ( \vec { \Gamma } , \vec { \Psi } )$ dominating $\vec { L . } \mathrm { A s } v ^ { \vec { L ^ { \prime } } } = n + 1$ , path $\vec { P } ^ { \vec { L ^ { \prime } } } \in \vec { \mathcal { P } }$ and the total reduced cost of $\vec { P } ^ { L ^ { \prime } } \mathrm { i s } \ \leq \bar { c } ^ { \vec { P } } ;$ thus, $z \le \bar { c } ^ { \vec { P } }$ 

Suppose now that $j < | \vec { P } |$ . Let $\mathrm { \Sigma } _ { P } ^ {  }$ be the corresponding backward path for $\vec { P } . \mathrm { { B y } }$ Observation 1, the pair of labels $\vec { L ^ { \prime } } = \vec { L _ { i } ^ { P } }$ and $\stackrel {  } { L } { } ^ { \prime } = \stackrel {  } { L } _ { | \bar { P } | - j - 1 } ^ { \tilde { P } }$ is ω-compatible for all $\omega > \bar { c } ^ { \vec { P } }$ . As both $\dot { \vec { P } }$ and $\mathbf { \Sigma } _ { P } ^ {  }$ are improving, by Proposition 1 either $\vec { L ^ { \prime } } \in$ $\vec { \bf L } ( \vec { \Gamma } , \vec { \Psi } )$ or there exists a label $\check { \vec { L } } \in \vec { \mathbf { L } } ( \vec { \Gamma } , \vec { \Psi } )$ dominating $\vec { L ^ { \prime } } .$ Also, by Proposition 1 either $\stackrel {  } { L } ^ { \prime } \in \stackrel {  } { \mathbf { L } } ( \stackrel {  } { \Gamma } , \stackrel {  } { \Psi } )$ or there exists a label $\stackrel {  } { L } \in \vec { \mathbf { L } } ( \stackrel {  } { \Gamma } , \stackrel {  } { \Psi } )$ dominating $\overleftarrow { L } ^ { \prime } .$ . Thus, by Proposition 2 there exists a pair of ω-compatible labels generated by the modified labeling algorithm for all $\omega > \bar { c } ^ { \vec { P } }$ . Then $\bar { c } ( \vec { P } ^ { L } \lVert \overleftarrow { P } ^ { L } ) \leq \bar { c } ^ { \vec { P } } ;$ thus, $z \le \bar { c } ^ { \vec { P } }$ □ 

We have just shown that our labelling algorithm can be modified in order to satisfy Condition (14) by adding jump bucket arcs. Now it can be executed over any superset Γ of improving bucket arcs. In the next section, weshowhowwecanfindnonimprovingbucketarcs, which then can be safely removed from the current set Γ. 

## 4.2. A Procedure for Finding

## Nonimproving Bucket Arcs

In this paragraph, we temporarily consider again all subproblems $( \bar { \mathrm { P S P } } ^ { m } )$ $m \in M$ . Let $( \bar { \pi } , \bar { \mu } , \bar { \nu } , \bar { \sigma } )$ be the current dual solution of the restricted linear relaxation of (SPF) together with Constraints (5) and (7). Suppose that the current sets $\Gamma ^ { m } , \ m \in M ,$ , include all improving buckets arcs, and the modified labelling algorithms have returned values $z ^ { m }$ for all $m \in M$ 

By Proposition $^ { 3 , }$ the modified labeling algorithm satisfies Condition (14). Thus, the value $L \bar { B }$ given by (15) is a valid lower bound for (SPF). A necessary condition for a path $P \in \Omega _ { m }$ to be improving is $L B - \operatorname * { m i n } \{ 0 , z ^ { m } \} + \dot { \bar { c } } ^ { P } ( \bar { \pi } , \bar { \mu } , \bar { \nu } , \bar { \sigma } ) < U B$ . We denote 

$$
\theta^ {m} = U B - L B + \min \{0, z ^ {m} \}.\tag{16}
$$

Then, a sufficient condition for a forward/backward path $P \in \Omega _ { m }$ to be nonimproving is $\bar { c } ^ { P } ( \bar { \pi } , \bar { \mu } , \bar { \nu } , \bar { \sigma } ) \geq \theta ^ { m }$ The next proposition gives a necessary condition for a bucket arc to be improving. The proposition applies both forward and backward. In the following, we drop again index m. 

For a (jump) bucket arc $\gamma \in \Gamma \ ( \psi \in \Psi )$ , we denote as $\overleftrightarrow { b } _ { \gamma } ^ { \mathrm { a r r } } \ ( \overleftrightarrow { b } _ { \psi } ^ { \mathrm { a r r } } )$ its arrival bucket of the opposite sense. For a forward bucket arc $\vec { \gamma } \in \vec { \Gamma } ,$ , its arrival bucket $\overleftarrow { b } _ { \vec { \gamma } } ^ { \mathrm { a r r } }$ is the backward bucket such that $\tilde { u } _ { \bar { b } } \geq$ max $\{ \tilde { l } _ { \vec { b } _ {  } } + d _ { \vec { a } _ {  } } , l _ { v ^ { \prime } } \} > \tilde { u } _ { \bar { b } } ^ {  } - \tilde { d } ,$ , where $v ^ { \prime }$ is the head node of ${ \vec { a } } _ { \vec { \gamma } } .$ . For a backward bucket arc $ $ , its arrival bucket $\vec { b } _ { \mathcal { V } } ^ { \mathrm { a r r } }$ is the forward bucket $\vec { b }$ such that $\tilde { l } _ { \vec { b } } \leq \operatorname* { m i n } \{ \tilde { u } _ { \vec { b }  } ^ { \prime } - d _ { \tilde { a } \stackrel {  } { \gamma } } , u _ { v ^ { \prime } } \} < \tilde { l } _ { \vec { b } } + \tilde { d } _ { \cdot }$ , where $v ^ { \prime }$ is the head node of a . For a jump bucket arc $\psi \in \Psi .$ , the definition of its forward/backward arrival buckets is identical, just replacing $b _ { \gamma }$ by $b _ { \psi } ^ { \mathrm { j u m p } }$ 

Proposition 4. Let the set Γ be a superset of all improving bucket arcs, let Ψ be the set of jump bucket arcs computed using procedure ObtainJumpBucketArcs Γ , and let the value θ be calculated according to (16). $I f \gamma \in \Gamma$ is an improving bucket arc, then there exists a pair of θ-compatible labels $L \in \mathbf { L }$ and $\stackrel {  } { L } \in \stackrel {  } { \mathbf { L } }$ such that $b ^ { L } \preceq b _ { \gamma }$ <sub>γ</sub> and $\overleftrightarrow { b } ^ { L } \preceq \overleftrightarrow { b } _ { \gamma } ^ { \mathrm { a r r } } .$ where $\mathbf { L } = \mathbf { L } ( \Gamma , \Psi )$ and $\overleftrightarrow { \mathbf { L } } = \overleftrightarrow { \mathbf { L } } ( \Gamma , \Psi )$ 

Proof. If γ is an improving bucket arc, then there exist an improving path $P$ and an index $j , 0 \leq j < | P | .$ , such that $\bar { b _ { \gamma } } =  { b ^ { L _ { j } ^ { P } } }$ and $a _ { \gamma } = a _ { j + 1 } ^ { P }$ . We have $\bar { c } ^ { P } < \theta ,$ , as it is a necessary condition for $P$ to be improving. Then by Observation 1, labels $L _ { i } ^ { P }$ and $\stackrel {  } { L } _ { | P | - j - 1 } ^ { P }$ are θ-compatible. If label $L _ { j } ^ { P } \in \mathbf { L } .$ , let $L = { \bf \check { \cal L } } _ { j } ^ { P } ; $ otherwise by Proposition 1 there exists a label $L \in \mathbf { L }$ dominating $L _ { j } ^ { P }$ such that $b ^ { L } \preceq b ^ { L _ { j } ^ { P } } = b _ { \gamma }$ . If label $\stackrel {  } { L } _ { | P | - j - 1 } ^ { P } \in \stackrel {  } { \mathbf { L } } ,$ , let $\begin{array} { r } { \stackrel {  } { L } = \stackrel {  } { L } _ { | P | - j - 1 } ^ { P } ; } \end{array}$ otherwise by Proposition 1 there exists $\underset {  } { \mathrm { ~ a ~ } }$ label $\stackrel {  } { L } \in \stackrel {  } { \mathbf { L } }$ ↔ ↔P dominating $\stackrel {  } { \overleftrightarrow { L } } _ { | P | - j - 1 } ^ { P }$ such that $\overleftrightarrow { b } ^ { L } \preceq \overleftrightarrow { b } ^ { L _ { | P | - j - 1 } } \preceq \overleftrightarrow { b } _ { \nu } ^ { \mathrm { a r r } }$ . Finally, by Proposition 2, L and $\overleftrightarrow { L }$ are θ-compatible. □ 

Proposition 4 is the basis for the procedure to find nonimproving bucket arcs. To prove that a bucket arc $\gamma$ is nonimproving, we need to verify that there does not exist a pair of θ-compatible labels $L \in \mathbf { L }$ and $\stackrel {  } { L } \in \stackrel {  } { \mathbf { L } }$ (one at the tail and one at the head of $a _ { \gamma } )$ such that $b ^ { L } \preceq b _ { \gamma }$ and $\begin{array} { r } { \overleftrightarrow { b } ^ { L } \preceq \overleftrightarrow { b } _ { \nu } ^ { \mathrm { a r r } } } \end{array}$ . A direct approach is to exhaustively enumerate all such pairs of labels for every bucket arc and verify whether at least one of them is θ-compatible. This approach is slow as the same pair of labels may be verified for θ-compatibility several times. 

To guarantee that no pair of labels is verified for θ-compatibility more than once, we compute sets $\stackrel {  } { B } _ { a , b }$ of buckets of opposite sense with respect to bucket b at the head of arc $^ { a . }$ Then $\stackrel {  } { B } _ { a , b }$ contains every bucket $\overleftrightarrow { b }$ containing a label $\overleftrightarrow { L } \in \overleftrightarrow { \mathbf { L } }$ which is θ-compatible with a label $L \in { \bar { \mathbf { L } } }$ such that $b ^ { L } \preceq b$ . Thus, a bucket arc $\gamma$ is nonimproving if $\overleftrightarrow { B } _ { a _ { \gamma } , b _ { \gamma } }$ does not contain any bucket $\begin{array} { r } { \overleftrightarrow { b } \preceq \overleftrightarrow { b } _ { \gamma } ^ { \mathrm { a r i } } } \end{array}$ . An advantage of using sets $\stackrel {  } { B } _ { a , b }$ is that they can be constructed incrementally using adjacent buckets: every set $\stackrel {  } { B } _ { a , b }$ is initialized as the union of sets $\overleftrightarrow { B } _ { a , b ^ { \prime } } , b ^ { \prime } \in \Phi _ { b } ,$ and then augmented using the recursive procedure UpdateBucketsSet, which tries to find opposite sense buckets containing a label which is θ-compatible with a label in b. 

We now present formally the auxiliary procedure UpdateBucketsSet(θ, $L , \overleftrightarrow { B } , \overleftrightarrow { b } , \overleftrightarrow { \mathbf { L } } , \overleftrightarrow { B } ^ { v i s i t e d } )$ , which adds to set <sup>↔</sup>B all opposite sense buckets $\overleftrightarrow { b ^ { \prime } } , \overleftrightarrow { b ^ { \prime } } \leq \overleftrightarrow { b } , \overleftrightarrow { b ^ { \prime } } \notin \overleftrightarrow { B } ^ { v i s i t e d } ,$ containing at least one label $\stackrel {  } { L } \in \stackrel {  } { \mathbf { L } }$ such that the pair $( L , \overleftrightarrow { L } )$ of labels is θ-compatible. 

Procedure UpdateBucketsSet (θ, L, $\overleftrightarrow{B}$ , $\overleftrightarrow{b}$ , $\overleftrightarrow{L}$ , $\overleftrightarrow{B}^{visited}$ ) $\overleftrightarrow{B}^{visited} \leftarrow \overleftrightarrow{B}^{visited} \cup \{\overleftrightarrow{b}\}$ if $\bar{c}^{L} + \bar{c}_{v^{\perp},\tilde{v}\leftrightarrow_{b}} + \bar{c}_{\overleftrightarrow{b}}^{best} \geq \theta$ then return
if $\overleftrightarrow{b} \notin \overleftrightarrow{B}$ then
    foreach label $\overleftrightarrow{L} \in \overleftrightarrow{L}$ , $\overleftrightarrow{b}^{L} = \overleftrightarrow{b}$ do
    if pair $(L, \overleftrightarrow{L})$ is $\theta$ -compatible then $\overleftrightarrow{B} \leftarrow \overleftrightarrow{B} \cup \{\overleftrightarrow{b}\}$ foreach $\overleftrightarrow{b'} \in \Phi_{\overleftrightarrow{b}} \setminus \overleftrightarrow{B}^{visited}$ do
UpdateBucketsSet( $\theta, L, \overleftrightarrow{B}, \overleftrightarrow{b'}, \overleftrightarrow{L}, \overleftrightarrow{B}^{visited}$ ) 

Finally, we formally present the main procedure BucketArcElimination(θ, Γ, Ψ, L, L), which can be used both forward and backward. The procedure removes some nonimproving bucket arcs from $\Gamma ,$ given the set L of nondominated labels of the same sense as Γ and the set $\stackrel {  } { \mathbf { L } }$ of nondominated labels of the opposite sense. In this procedure, in order to do the correct initialization of sets $\overleftrightarrow { B } _ { a , b } ,$ buckets b are considered in a lexicographic order of $\kappa ^ { b }$ . When considering bucket b, procedure UpdateBucketsSet is called to augment set $\stackrel {  } { B } _ { a , b }$ only if there exists $\gamma \in \operatorname { I }$ such that $b = b _ { \nu }$ or there exists $\boldsymbol { \psi } \in \boldsymbol { \Psi }$ such that $b = b _ { \psi } ^ { \mathrm { b a s e } }$ . If such a (jump) bucket arc does not exist, there is no bucket arc $\gamma ^ { \bar { \prime } } \in \Gamma$ such that $a _ { \gamma ^ { \prime } } = a$ and $b _ { \gamma ^ { \prime } } \geq b ,$ , and thus, the set $\stackrel {  } { B } _ { a , b }$ is not used in the reminder of the algorithm. 

Procedure BucketArcElimination (θ, Γ, Ψ, L, L) 

foreach bucket $b \in B$ in a lexicographic order of $\kappa^{b}$ do
    if $\Phi_{b} \neq \emptyset$ then
    foreach arc $a = (\tilde{v}_{b}, v') \in A$ do $\overleftrightarrow{B}_{a,b} \leftarrow \cup_{b' \in \Phi_{b}} \overleftrightarrow{B}_{a,b'}$ else
    foreach arc $a = (\tilde{v}_{b}, v') \in A$ do $\overleftrightarrow{B}_{a,b} \leftarrow \emptyset$ foreach jump bucket arc $\psi \in \Psi$ such that $b_{\psi}^{base} = b$ do
    foreach L ∈ L such that $b^{L} = b$ do
    UpdateBucketsSet( $\theta, L, \overleftrightarrow{B}_{a_{\psi},b}, \overleftrightarrow{b}_{\psi}^{arr}, \overleftrightarrow{L}, \emptyset$ )
    foreach bucket arc $\gamma \in \Gamma$ such that $b_{\gamma} = b$ do
    foreach L ∈ L such that $b^{L} = b$ do
    UpdateBucketsSet( $\theta, L, \overleftrightarrow{B}_{a_{\gamma},b}, \overleftrightarrow{b}_{\gamma}^{arr}, \overleftrightarrow{L}, \emptyset$ )
    if $\exists \overleftrightarrow{b'} \in \overleftrightarrow{B}_{a_{\gamma},b}: \overleftrightarrow{b'} \leq \overleftrightarrow{b}_{\gamma}^{arr}$ then $\Gamma \leftarrow \Gamma \setminus \{\gamma\}$ 

The next proposition proves formally that the combination of the modified bidirectional labeling algorithm and the bucket arc elimination procedure is correct, that is, it allows us to compute a valid lower bound for the original problem. 

Proposition 5. Let z be the reduced cost of the best path obtained by the modified bidirectional labeling algorithm after performing j calls to procedure BucketArcElimination, for some integer $j \geq 0$ . Then $z \le c ^ { \vec { P } }$ for any improving path $\vec { P } \in \vec { \mathcal { P } }$ 

Proof. It is enough to prove that the current forward (backward) bucket graph obtained after $j$ calls of procedure BucketArcElimination is a superset of all improving forward (backward) bucket arcs, since this proposition is a direct consequence of this statement and Proposition 3. We prove it by induction on the value of j. For $j = 0$ , this is true because the initial forward and backward bucket graphs contain all possible forward and backward bucket arcs. Now, suppose by inductive hypothesis that this is valid for $j = i ,$ that is, all improving forward (backward) bucket arcs are present in the current forward (back ward) bucket graph before the i 1 th call to procedure BucketArcElimination. Then, by Proposition 4, for each improving (forward or backward) bucket arc $\gamma \in \Gamma .$ , the bidirectional labeling algorithm generates a pair of θ-compatible labels L and $\overleftrightarrow { L }$ such that $b ^ { L } \preceq b _ { \gamma }$ and $\stackrel {  } { b } ^ { L } \leq \stackrel {  } { b } _ { \gamma } ^ { \mathrm { a r r } }$ . Since this pair of labels prevents $\gamma$ from being removed in the next call to procedure BucketArcElimination, the statement under consideration is also true for $j = i + 1$ , finishing the proof. □ 

Let us now give some final remarks on our bucket arc elimination procedure. 

• Our procedure can be viewed as a “resource-valuedependent” arc elimination and thus a generalization of the arc elimination procedure by Irnich et al. (2010). The latter can readily be extended to remove extensions of forward labels $\vec { L }$ along an arc if $q ^ { \vec { L } }$ exceeds a certain threshold (and extensions of backward labels $\stackrel {  } { L }$ if $q ^ { \stackrel {  } { L } }$ is lower than a threshold). The opposite case (“less than” for the forward sense and “greater than” for the backward sense) is more complicated as shown in our analysis and necessitates introduction of jump bucket arcs. Our procedure is more generic than both and may result in elimination of disjoint “resource-value” intervals for an arc. 

• The necessity of adding jump bucket arcs essentially comes from the fact that the dominating label may be contained in a different bucket than the dominated one. Restricting dominance checks only to ones within buckets does not make jump bucket arcs redundant. This is because two labels in the same bucket with distinct resource consumption may be extended (over the same arc) to labels in different buckets. Only restricting the dominance checks to labels with the same resource consumption allows one to skip the generation of jump bucket arcs. 

• “Resource-value-dependent” arc elimination has already been used in Pessoa et al. (2010) and in Pecin et al. (2017b). However, it was done at the cost of resource discretization, limited dominance checks, and dropping the exploitation of the route symmetry. Our procedure allows one to overcome these drawbacks. 

## 5. BCP Algorithm

We implemented a BCP algorithm for the HFVRPTW using the new bucket graph labeling algorithm in its pricing and also the bucket arc elimination procedure. Other algorithmic elements are similar to the ones presented in Pecin et al. (2014; 2017a, b), except that a different dynamic ng-relaxation by Bulhoes, Sadykov, and Uchoa (2018) and automatic dual price smoothing stabilization by Pessoa et al. (2018) are used. However, to make this paper self-contained, we give a succinct algorithm description in what follows. 

For each node of the branch-and-bound tree, the lower bound on the optimal cost is computed by solving the linear relaxation of SPF enhanced with RCCs (5) and limited-memory R1Cs (7). Before each cut generation round, the current SPF relaxation is solved through column generation, using automatic (parameterless) dual pricing smoothing stabilization described in Pessoa et al. (2018). To further speed up the convergence, three-stage column generation is implemented. At the first stage, the “light” pricing heuristic is applied in which only one label per bucket (with the smallest reduced cost) is kept. In the second stage, a more expensive pricing heuristic is used in which states $\mathcal { F }$ and $\mathcal { G }$ are not taken into account in the dominance checks. In the last stage, the exact labeling algorithm is used. We use parameters $\chi ^ { \mathrm { h e u r } }$ <sup>r</sup> and χ<sup>exact</sup> $\chi$ for the maximum number of columns generated at each iteration of column generation at heuristic and exact stages. When the number of columns in the restricted master exceeds $\chi ^ { \mathrm { m a x } }$ , we clean them up and leave only $\chi ^ { \mathrm { p e r c o } } / \mathrm { c }$ columns with the least reduced cost. Basic columns are never removed, however, nonbasic columns with zero reduced cost may be removed. 

The threshold value $q ^ { * }$ for the bidirectional labeling algorithm is an important parameter in the nonsymmetric case. Pecin et al. (2017b) and Tilk et al. (2017) proposed to modify the labeling algorithm to automatically adjust this value. Here we adopt a simpler but still effective approach. Value $q ^ { * }$ is initialized as the average middle value for all resource consumption intervals. After each exact pricing we compare the number of forward and backward nondominated labels. If one number exceeds another by more than 20%, we adjust value $q ^ { * }$ by moving it by 5% toward the maximum or minimum possible value. 

The limited-arc-memory R1Cs are separated by complete enumeration for $| C | \le 3$ and by a local search heuristic for $| C | \in \{ 4 , 5 \}$ . This heuristic is launched for each possible optimal vector $p .$ Given $p ,$ it tries to find a subset C of customers such that the corresponding cut is violated with a full memory. If such C exists, an arc memory AM of minimal size such that the cut violation remains the same is identified. Then AM is completed with all arcs between vertices in C. When there already exists a cut defined for the same subset C and vector p but for a different arc memory AM, this cut is simply enhanced by enlarging its memory to AM AM. Otherwise, a new cut is added for $C , p ,$ , and AM. Also, memory sets are kept symmetric. This means that if arc $( \bar { v } , v ^ { \prime } )$ is added to AM, then $( v ^ { \prime } , v )$ must also be added. The parameter vector $\beta$ is used in which $\beta _ { k } , k \in \{ 0 , 1 , 3 , 4 , 5 \}$ is the maximum number of R1Cs with $| C | = k$ separated per round. (Specifically, $\beta _ { 0 }$ is the maximum number of RCCs generated per round.) Other parameters are $\delta ^ { \mathrm { p e r c o } } / \mathrm { o }$ and $\delta ^ { \mathrm { n u m } }$ , which are used to stop the cut generation by tailing off. If the number of times the dual-primal gap is decreased by less than $\delta ^ { \mathrm { p e r c o } } / \mathrm { o }$ reaches δ<sup>num</sup>, then branching is performed. At the root node, R1Cs are separated only if RCCs are exhausted or tail off. Before each cut round, nonactive cuts are removed from the restricted master. 

In our algorithm, we use the dynamic variant of the ng-relaxation from Bulhoes, Sadykov, and Uchoa (2018), which is inspired by Roberti and Mingozzi (2014). We start by generating the ng-neighborhood for each customer by including their $\breve { \eta } ^ { \mathrm { i n i t } }$ closest customers (including itself). After the convergence of column generation, we augment neighborhoods in order to forbid the cycles in the columns forming the current fractional solution. For each such column we either forbid all cycles of size at most $\eta ^ { 1 } \mathrm { o r } ,$ if there is no one, the minimum size cycle. Cycles in at most $\eta ^ { 2 }$ columns are forbidden in each round. After augmentation, the neighborhood of any customer cannot exceed size $\eta ^ { \mathrm { m a x } }$ . The same tailing-off parameters as for cutting planes are applied here. We start the root node by augmenting the ng-neighborhoods. Then, after tailing off, RCCs are separated. After another tailing off, R1Cs are separated. 

Each additional active inequality (7) or ngneighborhood increase makes the pricing problem harder, since they weaken the dominance conditions. Because of that, separation is stopped when the average exact pricing time since the last round of cuts has exceeded a given threshold τ. In some extreme cases, R1Cs may be even removed by the rollback procedure activated if a single pricing time exceeds τ¯, as described in Pecin et al. (2017b). 

The bucket arc elimination procedure is called after the initial convergence and also each time the current primal-dual gap decreased by at least υ% since the last call to the bucket arc elimination procedure. A bidirectional enumeration procedure is called after each reduced cost fixing to try to generate all improving elementary routes, using the algorithm proposed in Pecin et al. (2017b). The enumeration procedure is interrupted if the number of labels exceeds $\omega ^ { \mathrm { l a b e l s } }$ or if the number of enumerated routes exceeds $\omega ^ { \mathrm { r o u t e s } }$ After a successful enumeration for a pricing subproblem corresponding to a vehicle type, this subproblem passes to the enumerated state and the (SPF) relaxation is updated by excluding nonelementary columns coming from this subproblem. The pricing subproblem in the enumerated state is solved by inspection. The elimination procedure based on reduced costs is also performed for enumerated pricing subproblems, but considering each route individually. If after some point, the number of enumerated routes for each subproblem falls below $\omega ^ { \mathrm { M I P } }$ , the residual problem is handled by a standard mixedinteger programming (MIP) solver, and the node is considered to be processed. 

Recall that, to perform bucket arc elimination, the forward and backward passes of the labeling algorithm must be performed completely. Thus, the running time may increase substantially in comparison with the “standard” exact labeling in which the labels beyond the bidirectional threshold value $q ^ { * }$ are not stored. To accelerate the bucket arc elimination procedure, we use what we call the exact completion bounds technique: for every label L just created by extension beyond $q ^ { * }$ , we verify exhaustively whether there exists an opposite sense label L such that L and L are θ-compatible. Here θ is as defined in Section 4. We keep label L only if such a label $\overleftrightarrow { L }$ exists. A similar approach is used in the bidirectional enumeration procedure. Using completion bounds for labels before the bidirectional threshold value $q ^ { * }$ , as is done in Pecin et al. (2017b), was not computationally beneficial for our labeling algorithm. 

If the tailing-off condition is attained or if at least one nonenumerated pricing subproblem becomes too time consuming to solve, branching must be performed. This is done by adding constraints to the master linear program (LP) that correspond to tightening the lower and upper bounds on the value of the following aggregated variables corresponding to different branching strategies: 

$$
\bullet \quad g ^ {m} = \sum_ {P \in \Omega_ {m}} \sum_ {v ^ {\prime} \in V ^ {\prime}} \frac {1}{2} (x _ {(0, v ^ {\prime})} ^ {P} + x _ {(v ^ {\prime}, n + 1)} ^ {P}) \lambda_ {P},   m \in M;
$$

$$
\bullet \quad g _ {v} ^ {m} = \sum_ {P \in \Omega_ {m}} y _ {v} ^ {P} \lambda_ {P}, v \in V, m \in M; \text { and }
$$

$$
\bullet g _ {\{v, v ^ {\prime} \}} = \sum_ {m \in M} \sum_ {P \in \Omega_ {m}} (x _ {(v, v ^ {\prime})} ^ {P} + x _ {(v ^ {\prime}, v)} ^ {P}) \lambda_ {P}, v, v ^ {\prime} \in V, v \neq v ^ {\prime}.
$$

Here, $g ^ { m }$ corresponds to the number of used vehicles of type m $\in M ; g _ { v } ^ { m }$ characterizes whether customer $v \in V$ is served by a vehicle of type $m ;$ and $g _ { \{ v , v ^ { \prime } \} }$ characterizes whether edge $( v , v ^ { \prime } )$ is used in the solution. Given a fractional branching variable $g$ with fractional valuef, two child nodes are created by adding the constraints $g \leq \lfloor f \rfloor$ and $g \geq \lceil f \rceil$ to the master LP A generic branching constraint $\begin{array} { r } { \sum _ { m \in M } \sum _ { P \in \Omega _ { m } } \sum _ { a \in A } \alpha _ { a } ^ { m } } \end{array}$ $x _ { a } ^ { P } \lambda _ { P } \geq \beta$ added to the master LP with associated dual variable ι can be considered by the pricing algorithm by subtracting $\iota \alpha _ { a } ^ { m }$ from the expression of $\bar { c } _ { a } ^ { m } ( \pi , \nu )$ given by (8), for each $m \in M$ and $a \in A$ , thus, not making the subproblem harder. Moreover, the symmetry is preserved, as for any route $P ,$ coefficients $x _ { ( v ^ { \prime } , v ) } ^ { P }$ and $x _ { ( v , v ^ { \prime } ) } ^ { P }$ are the same in all branching constraints we use. 

The selection of the branching variable at each node is done using a sophisticated hierarchical evaluation strategy similar to the one proposed in Pecin et al. (2017b). The idea is to spend more time evaluating branching variables in the lowest levels of the branchand-bound tree where each selection has a greater impact on the overall time, and spend less time as the level increases, taking advantage of the history of previous evaluations. The following three evaluation phases are used: 

• Phase 0: Half of the candidates are chosen from history using pseudocosts (if history is not empty). The remaining candidates are chosen in a balanced way between the three strategies. Within the same strategy, the candidates are chosen based on the distance from its fractional value to the closest integer, and for branching on edges, distance to the closest depot (the smaller the better) is also taken into account. 

• Phase 1: Evaluate the selected candidates from phase 0 by solving the current restricted master LP modified for each created child node, without gen erating columns. Select the variables with the maximum value of $\Delta L B _ { 1 } \times \Delta L B _ { 2 }$ , where $\Delta L B _ { i }$ denotes the increase in the current lower bound obtained for the ith child node, for $i = 1 , 2$ (Achterberg 2007, product rule). 

• Phase 2: Evaluate the selected candidates from phase 1 by solving the relaxation associated to each created child node, including heuristic column generation, but not cut generation. The best candidate is also selected by the product rule. 

The parameters $\zeta _ { 0 }$ and $\zeta _ { 1 }$ are used to specify the maximum number of candidates chosen in phases 0 and 1. 

## 6. Computational Experiments

The BCP algorithm described in the previous section was coded in C++ and compiled with GCC 5.3.0. The BaPCod package by Vanderbeck, Sadykov, and Tahiri (2017) was used to handle the BCP framework. IBM CPLEX Optimizer version 12.6.0 was used as the LP solver in column generation and as the IP solver for the set partitioning problem with enumerated columns. The experiments were run on a 2 Deca-core Ivy-Bridge Haswell Intel Xeon E5-2680 v3 server running at 2.50 GHz. The 128 GB of available RAM was shared between eight copies of the algorithm running in parallel on the server. Each instance is solved by one copy of the algorithm using a single thread. 

## 6.1. Instances

6.1.1. VRPTW Instances. For the VRPTW, the 14 most difficult (according to Pecin et al. 2017a) instances with 100 customers from Solomon (1987), as well as 120 instances by Gehring and Homberger (2002) with 200 and 400 customers were considered. The Solomon instances used were C203, C204, RC204, RC207, RC208, R202, R203, R204, R206, R207, R208, R209, R210, and R211. The instances were divided into classes of clustered (C), random-clustered (RC), and random (R) locations of customers on the plane. Note that the capacity resource in Solomon instances, as well as in Gehring and Homberger instances of classes C2, RC2, and R2, is not tight. Therefore, the subproblem only considers the time resource in those classes. The capacity resource is imposed through RCCs, which are treated as “core” cuts, that is, they are separated for every fractional but also for every integer solution found during the search. The initial upper bounds were obtained by the heuristic in Vidal et al. (2013). The same values were used in Pecin et al. (2017a). 

6.1.2. DCVRP Instances. For the DCVRP, we used seven classic instances from Christofides, Mingozzi, and Toth (1979) with 50–200 customers, named CMT6, CMT7, CMT8, CMT9, CMT10, CMT13, and CMT14. In spite of the fact that those instances are ubiquitous in the heuristic literature for the CVRP, no exact methods could solve them. The best known solutions for these instances are taken from Nagata and Braysy (¨ 2009). 

6.1.3. MDVRP Instances. For the MDVRP, we used 22 standard distance constrained instances by Cordeau, Gendreau, and Laporte (1997) with 80–360 customers, named p08–p11, p13, p14, p15, p17, p19, p20, p22, p23, and pr01–pr10. The best known solutions are taken from Vidal et al. (2012). 

6.1.4. SDVRP Instances. For the SDVRP, we used 10 standard distance constrained instances by Cordeau and Laporte (2001) with 48–288 customers, named pr01–pr10. The best known solutions are taken from Cordeau and Maischberger (2012). 

6.1.5. HFVRP Instances. For the HFVRP, we used 96 instances by Duhamel, Lacomme, and Prodhon (2011) with 20–256 customers. Each instance represents one of 96 French departments. The best known solutions are taken from Penna et al. (2019). Following the convention used in those papers, we impose that each customer should be visited exactly once. As the triangle inequality is not always satisfied in those instances, allowing multiple visits could possibly reduce costs. 

## 6.2. Algorithm Parameterization

The column management parameter values are $\chi ^ { \mathrm { h e u r } } =$ 30, $\chi ^ { \mathrm { e x a c t } } = 1 5 0 , \ \tilde { \chi } ^ { \mathrm { m a x } } = 1 \hat { 0 } , 0 0 0 , \ \chi ^ { \mathrm { p e r c } } = 6 6 \%$ . The cut generation parameter values are $\beta _ { 0 } = \beta _ { 1 } = \beta _ { 4 } = \beta _ { 5 } = 1 0 0 ,$ $\bar { \beta } _ { 3 } = 1 5 0$ . The tailing off parameter values are $\delta ^ { \mathrm { p e r c } } =$ $1 . 5 \% , \delta ^ { \mathrm { n u m } } = 3$ . The dynamic ng-relaxation parame ter values are $\eta ^ { \mathrm { i n i t } } = { \bf \bar { 8 } } , \ \eta ^ { 1 } = 5 , \ \eta ^ { 2 } = 1 0 0 , \ \eta ^ { \mathrm { i n a x } } = 1 6 .$ The parameter value for the bucket graph elimination procedure is υ 10%. Enumeration parameter values are $\omega ^ { \mathrm { l a b e l s } } = 1 , 0 0 0 , 0 0 0 , \quad \omega ^ { \mathrm { r o u t e s } } = \mathbf { \hat { 5 } } , 0 0 0 , 0 0 0 ,$ $\omega ^ { \mathrm { M I P } } = 1 0 , 0 0 0$ . The strong branching parameter values are $\zeta _ { 0 } = 5 0 , \zeta _ { 1 } = 3 .$ 

The pricing time thresholds (in seconds) depend on instance class, instance size, and the number of resources (dimension of buckets). These thresholds as well as which resource is chosen as $r ^ { * }$ in bidirectional labeling are shown in Table 1. 

The bucket step size in the labeling algorithm is adjusted dynamically as explained below. 


Table 1. Parameterization of Pricing Time Thresholds


<table><tr><td>Problem</td><td>Size</td><td>Resources</td><td><eq>\tau</eq></td><td><eq>\bar{\tau}</eq></td><td><eq>r^{*}</eq></td></tr><tr><td>VRPTW</td><td>100–200</td><td>1</td><td>10</td><td>30</td><td>Time</td></tr><tr><td>VRPTW</td><td>200</td><td>2</td><td>5</td><td>10</td><td>Capacity</td></tr><tr><td>VRPTW</td><td>400</td><td>1</td><td>20</td><td>60</td><td>Time</td></tr><tr><td>VRPTW</td><td>400</td><td>2</td><td>10</td><td>20</td><td>Capacity</td></tr><tr><td>HFVRP</td><td>Any</td><td>1</td><td>5</td><td>10</td><td>Capacity</td></tr><tr><td>DCVRP</td><td>Any</td><td>2</td><td>5</td><td>10</td><td>Time</td></tr><tr><td>MDVRP, SDVRP</td><td>Any</td><td>2</td><td>10</td><td>20</td><td>Time</td></tr></table>

## 6.3. Impact of Bucket Step Size and Improved Arc Elimination

As the two main contributions of the paper are using the bucket graph in the labeling algorithm and the improved procedure for bucket (or resourcevalue-dependent) arc elimination, we first estimate their impact on the solution time of the BCP algorithm. 

These tests were done on the set of 74 VRPTW instances with 100 and 200 customers. Only the root node is considered. For instances which cannot be solved at the root, we establish the “target” lower bounds. These values are determined in such a way that once they are achieved by a good parameterization of the algorithm in the root node, it is reasonable to branch, because either (i) cuts are tailing off or (ii) pricing becomes too expensive. In Table 2, we give the instance name, its optimum or best known solution (BKS) value, and the target lower bound (TLB) value we use. For instances not shown in this table, the target lower bound equals the optimum solution value. 

The first parameter we test is the “maximum number of buckets per vertex,” which we denote as ξ. For instances with one time resource, the bucket step size <sup>˜</sup>d is determined as $\left( u _ { n + 1 } - l _ { 0 } \right) / \xi$ . For instances with two resources, the bucket step size $\tilde { d } _ { 1 }$ for the first (capacity) resource is determined as $W / \sqrt { \xi } ,$ , and the bucket step size $\tilde { d } _ { 2 }$ for the second (time) resource is determined as $( u _ { n + 1 } - l _ { 0 } ) / \sqrt { \xi } .$ . All values $\tilde { \boldsymbol { d } } _ { \boldsymbol { r } }$ are rounded off to one decimal place. 

The second binary parameter to test is whether the standard arc elimination procedure (as in Irnich et al. 2010) or improved bucket arc elimination procedure is applied. In the former case, we fix arc $a \in A$ if all bucket arcs $\gamma \in \Gamma$ such that $a _ { \gamma } = a$ can be fixed. When fixing original arcs, no jump bucket arcs are needed. The procedure for fixing original arcs runs faster, as once we determine that a bucket arc $\gamma \in \Gamma$ cannot be fixed, we do not try to fix bucket arcs $\gamma ^ { \prime } \in \Gamma$ such that $a _ { \gamma ^ { \prime } } = a _ { \gamma }$ . The resource-value-dependent arc elimination procedure proposed in Pessoa et al. (2010) is valid only for the single resource case with positive integer consumption. This procedure can be viewed as a fixing in a bucket graph with unitary step size $( \tilde { d } = 1 )$ under the condition that the dominance checks are performed only between labels with the same resource consumption. 

The base variant of the algorithm which we denote as (O) is parameterized with $\dot { \boldsymbol { \xi } } = 1$ , which corresponds to the basic variant of the label-correcting algorithm, and uses the standard arc elimination procedure. The base variant is compared with variant (A), which is parameterized with a fixed value $\xi \in \Xi = \{ 1 , 2 ,$ $5 , \hat { 1 0 } , 2 0 , 5 0 , 1 0 0 , 2 0 0 , 5 0 0 , 1 , 0 0 0 , 2 , 0 0 0 \}$ and uses the standard arc elimination procedure. Then variant (A) is compared with variant (A+) which uses a procedure described below to automatically adjust value ξ and the standard arc elimination procedure. Finally variant (A+) is compared with variant $( \mathrm { A ^ { + } B } )$ , which uses dynamically adjusted value $\xi$ and the improved procedure for bucket arc elimination. 

Different bucket step sizes used in the labeling algorithm usually change the set of routes returned by the “light” pricing heuristic to the master. This happens because a given nondominated label may be extended if one bucket step size is used and not extended if another bucket step size is used. Thus, for each value $\xi ,$ the sequence of primal-dual master solutions in the column generation may follow a different trajectory and converge to distinct alternative optima. In turn, this changes the set of cuts returned by the separation procedures, affecting the pricing difficulty for the next iterations. This “random noise” may make the experimental results less clear. To avoid this, for each instance i I we choose a value $\bar { \boldsymbol { \xi } } _ { i } \in \Xi$ . Then, two copies of the labeling algorithm are created, the first one with value ${ \bar { \xi } } _ { i } ,$ and the second one with the value being tested $\xi ^ { \prime } \in \Xi$ . Both copies are executed in each iteration of column generation, and the arc elimination procedure is also performed twice. Only the set of columns produced by the first copy of the algorithm is sent to the master. Therefore, the sequence of primal-dual solutions of the master problem is always the same regardless of the bucket step size parameterization. 


Table 2. Target Lower Bounds for VRPTW Instances


<table><tr><td>Instance</td><td>BKS</td><td>TLB</td><td>Instance</td><td>BKS</td><td>TLB</td><td>Instance</td><td>BKS</td><td>TLB</td></tr><tr><td>R208</td><td>701.0</td><td>699.0</td><td>R2_2_4</td><td>1,928.5</td><td>1,925.0</td><td>RC1_2_7</td><td>3,177.8</td><td>3,150.0</td></tr><tr><td>C2_2_3</td><td>1,763.4</td><td>1,753.1</td><td>R2_2_6</td><td>2,675.4</td><td>2,674.6</td><td>RC1_2_8</td><td>3,060.0</td><td>3,044.8</td></tr><tr><td>C2_2_4</td><td>1,695.0</td><td>1,663.5</td><td>R2_2_8</td><td>1,842.4</td><td>1,831.6</td><td>RC1_2_9</td><td>3,074.8</td><td>3,035.7</td></tr><tr><td>R1_2_3</td><td>3,373.9</td><td>3,352.2</td><td>R2_210</td><td>2,549.4</td><td>2,545.3</td><td>RC1_210</td><td>2,990.5</td><td>2,966.0</td></tr><tr><td>R1_2_4</td><td>3,047.6</td><td>3,035.3</td><td>RC1_2_1</td><td>3,516.9</td><td>3,499.1</td><td>RC2_2_2</td><td>2,481.6</td><td>2,477.5</td></tr><tr><td>R1_2_5</td><td>4,053.2</td><td>4,044.1</td><td>RC1_2_2</td><td>3,221.6</td><td>3,204.4</td><td>RC2_2_4</td><td>1,854.8</td><td>1,848.0</td></tr><tr><td>R1_2_6</td><td>3,559.2</td><td>3,544.2</td><td>RC1_2_3</td><td>3,001.4</td><td>2,980.7</td><td>RC2_2_7</td><td>2,287.7</td><td>2,279.7</td></tr><tr><td>R1_2_8</td><td>2,938.4</td><td>2,933.3</td><td>RC1_2_4</td><td>2,845.2</td><td>2,832.8</td><td>RC2_2_8</td><td>2,151.2</td><td>2,150.3</td></tr><tr><td>R1_2_9</td><td>3,734.7</td><td>3,726.0</td><td>RC1_2_5</td><td>3,325.6</td><td>3,310.0</td><td>RC2_2_9</td><td>2,086.6</td><td>2,072.0</td></tr><tr><td>R1_210</td><td>3,293.1</td><td>3,277.3</td><td>RC1_2_6</td><td>3,300.7</td><td>3,289.0</td><td>RC2_210</td><td>1,989.2</td><td>1,962.0</td></tr></table>


Notes. BKS, best known solution; TLB, target lower bound.


- -- Subproblem time ratic $\varrho _ { \xi } ^ { s p }$ — Total time ratic $\varrho _ { \xi } ^ { t o t }$ 

For each run, that is, for each pair $( i , \xi ^ { \prime } )$ , we collect the following statistics. The first one is the ξ-subproblem time, which includes the time to create the bucket graph and a topological order for it, the total pricing time, the total bucket arc elimination time, and the total enumeration time. The second statistic is the master time which is the difference between the overall running time and the sum of the $\bar { \boldsymbol { \xi } } _ { i }$ -subproblem time and ξ-subproblem time. The total time is the sum of the master time and the ξ-subproblem time. For each instance i I and each value $\xi ^ { \prime } \in \Xi ,$ , we calculate ratios $\varrho _ { i , \xi ^ { \prime } } ^ { t o t }$ and $\varrho _ { i , \xi } ^ { s p }$ between the corresponding total time (or ξ-subproblem time) and the minimum total time (minimum ξ-subproblem time) among all $\xi \in \Xi$ for this instance. The ratio $\varrho _ { i , 1 } ^ { t o t }$ shows the speedup of variant (A) of the algorithm over variant (O) for instance i I. 

In Figure 5, we show the graphs for average and maximum values for both $\varrho _ { i , \xi ^ { \prime } } ^ { t o t }$ and $\varrho _ { i , \xi ^ { \prime } } ^ { s p }$ , as well as graphs for these ratios for some representative individual instances. The averages are shown for two groups of instances: with one and two resources. The time limit was reached for $\xi ^ { \prime } = 1 , 2 , 5 , 1 0 , 2 0 , 5 0 ,$ , 100 for $6 , 6 , 4 , 4 , 3 , 1 ,$ 1 instances, respectively. Therefore, average and maximum values given in Figure 5 for these values of $\xi$ are in fact their lower bounds. It can be seen from the figure that parameter $\xi$ has a large impact on the total pricing time, as well as on the overall time of the root node. The best value for this parameter is instance dependent. Two extreme values $\xi = 1$ and $\xi = 2 , 0 0 0$ are bad parameterizations on average. The variant (A) with parameterization $\xi = 2 0 0$ is at least 2.85 times faster on average than variant (O) The maximum speedup reaches at least the factor of 20. (It is expected to be much higher for instances stopped by the time limit.) 


Figure 5. Impact of Parameter ξ on the Subproblem and Total Time


In Figure 5, the graph for instance RC_1_2_5 also shows the interest in having smaller buckets than is required to make the extended bucket graph acyclic. For this instance, setting parameter ξ to 500 makes the extended bucket graph acyclic. However, the subproblem time and the total time of the algorithm are, respectively, 14% and 12% smaller for parameterization $\xi = 1 , 0 0 0$ . Most of the time, however, the best parameterization results in an extended bucket graph with cycles 

In variants (A+) and $( \mathrm { A ^ { + } B } )$ we use a scheme for dynamic adjustment of parameter ξ which outperforms the best parameterization $( \xi = 2 0 0 )$ for variant (A). In this scheme, we start with ξ 25. After each column generation convergence, we calculate the average ratio (among all calls to exact pricing) of the number of dominance checks between labels in a same bucket and the total number of nondominated labels. If this average ratio is larger than 500 and the average number of nonfixed bucket arcs (including jump bucket arcs) per vertex is less than 10,000, we multiply ξ by 2. After each such multiplication, the bucket graph is regenerated. 

In Figure 6(a), we show the performance profile which compares the total time for variant (A) with $\xi = 2 0 0$ and for variant (A+). The geometric mean of the speedup ratios between these two variants is 7%, and the maximum speedup reaches 83%. 

![](images/7bcb184d20e1dd95490b201735bb70fade47625891c9411aca8280f5b0f8643f.jpg)



Figure 6. Performance Profiles for Variants $( \mathrm { A } ) , ( \mathrm { A } ^ { + } ) ,$ , and (A+B) of the Algorithm



(a)


![](images/f9a44576f3908b5e4c20018331ce2c9021e3c77de9b050b11c3e1814164e13cc.jpg)



(b)


![](images/330d17501320dd0733ad6e614b6843dacdd5d94b1b312666db947751eabef334.jpg)



Total time for a variant is at most X times larger than the best


In Figure 6(b) we show the performance profile which compares variants (A+) and (A+B) of the algorithm. Although the geometric mean of the speedup ratios due to the improved bucket arc elimination procedure is only $6 \% ,$ for two instances the speedup ratio reaches the factor of two. These are instances $\mathrm { R } \hat { C } 2 \ 2 \ 8$ and R2_2_4. In general, we noticed that one significantly benefits from the improved bucket arc elimination procedure on hard instances with small primal-dual gaps. Such instances usually have long routes. 

Concluding this part, we summarize the results in Table 3. In it, we show the geometric means of total solution time ratios for different variants of the algorithm. There, variant (A) uses parameterization $\breve { \xi } = 2 0 0$ . We give these ratios for all 74 instances as well as for subclasses of instances. Looking at Table $^ { 3 , }$ we can say that the most computational improvement is achieved when passing from variant (O) to variant (A) of the algorithm provided that the bucket step size is well chosen. Additional speedups of variants (A+) and $\left( \mathrm { A } ^ { + } \mathrm { B } \right)$ are relatively small on average but can be significant for certain instances. 


Table 3. Comparison of Different Variants of the Algorithm on VRPTW Instances


<table><tr><td>Instances</td><td>(O)→(A)</td><td>(A)→(A+)</td><td>(A+)→(A+B)</td></tr><tr><td>100 customers</td><td>1.54</td><td>1.18</td><td>1.06</td></tr><tr><td>200 customers</td><td>2.01</td><td>1.04</td><td>1.07</td></tr><tr><td>Series 1</td><td>2.10</td><td>1.11</td><td>1.05</td></tr><tr><td>Series 2</td><td>1.78</td><td>1.03</td><td>1.07</td></tr><tr><td>Series C</td><td>1.20</td><td>1.21</td><td>1.03</td></tr><tr><td>Series R</td><td>1.62</td><td>0.99</td><td>1.08</td></tr><tr><td>Series RC</td><td>4.15</td><td>0.97</td><td>1.09</td></tr><tr><td>All</td><td>1.91</td><td>1.07</td><td>1.06</td></tr></table>

## 6.4. Impact of Two-Dimensional Buckets

As proposed in Section 3.3, labels are grouped into buckets based on the accumulated resource consumption values. Thus, if there are two resources, we can say that buckets are “two-dimensional.” However, buckets can also be formed based only on the accumulated consumption of only one of two resources. Implementation of the labeling algorithm and bucket arc elimination procedure with such “one-dimensional” buckets is easier as sets $\Phi _ { b } , b \in B ,$ contain at most one element. In this section we computationally verify whether an additional effort to implement two-dimensional buckets pays off, that is, results in improved algorithm efficiency. 

In this experiment, we use variant $( \mathrm { A } ^ { + } \mathrm { B } )$ of the algorithm to solve the root node of instances with two resources. As the number of VRPTW instances with two resources is only 30 (these are instances of classes C1, RC1, and R1), we add in this experiment MDVRP, SDVRP, and DCVRP instances. In total, we have 69 instances. As before, for instances which cannot be solved at the root, we set the “target” lower bounds, given in Table 4. Again, for instances not shown in the table, the target lower bound equals the optimum solution value. 

As before, when using two-dimensional buckets, given parameter $\xi ,$ bucket step size $\tilde { d } _ { 1 }$ is determined as $W / \sqrt { \xi }$ and $\tilde { { d } } _ { 2 }$ is calculated as $( u _ { n + 1 } - l _ { 0 } ) / \sqrt { \xi }$ . Naturally, when using one-dimensional buckets, “bucketization” is performed according to the most critical resource. This is the capacity resource for VRPTW instances $( \tilde { d } _ { 1 } = W / \xi )$ and the time resource for the remaining ones $( \tilde { d } _ { 1 } = ( u _ { n + 1 } - l _ { 0 } ) / \xi )$ ). The most critical resource can easily be determined by running a reasonable heuristic for the problem and analyzing the solutions obtained. As in the previous section, two copies of the labeling algorithm are created to obtain the same sequence of primal-dual solutions of the master problem for both tested configurations. 


Table 4. Target Lower Bounds for MDVRP, SDVRP, and DCVRP Instances


<table><tr><td>Instance</td><td>BKS</td><td>TLB</td><td>Instance</td><td>BKS</td><td>TLB</td><td>Instance</td><td>BKS</td><td>TLB</td></tr><tr><td>CMT8</td><td>865.94</td><td>857.8</td><td>mdvrp_p10</td><td>3,631.11</td><td>3,626.2</td><td>sdvrp_pr04</td><td>3,449.84</td><td>3,432.3</td></tr><tr><td>CMT9</td><td>1,162.55</td><td>1,153.3</td><td>mdvrp_p11</td><td>3,546.06</td><td>3,540.4</td><td>sdvrp_pr05</td><td>4,377.35</td><td>4,332.6</td></tr><tr><td>CMT10</td><td>1,395.85</td><td>1,387.3</td><td>mdvrp_pr05</td><td>2,331.20</td><td>2,318.7</td><td>sdvrp_pr06</td><td>4,422.02</td><td>4,329.2</td></tr><tr><td>CMT13</td><td>1,541.30</td><td>1,475.2</td><td>mdvrp_pr06</td><td>2,676.30</td><td>2,662.9</td><td>sdvrp_pr08</td><td>2,971.01</td><td>2,962.6</td></tr><tr><td>mdvrp_p08</td><td>4,372.78</td><td>4,347.6</td><td>mdvrp_pr10</td><td>2,868.26</td><td>2,860.6</td><td>sdvrp_pr09</td><td>3,536.20</td><td>3,527.7</td></tr><tr><td>mdvrp_p09</td><td>3,858.66</td><td>3,849.9</td><td>sdvrp_pr03</td><td>2,575.36</td><td>2,565.6</td><td>sdvrp_pr10</td><td>4,639.62</td><td>4,659.4</td></tr></table>


Notes. BKS, best known solution; TLB, target lower bound. 


In Figure 7 we show the performance profile which compares variants with one-dimensional and twodimensional buckets. The geometric means of the speedup ratios between the two variants is 21%. On individual instances the maximum speedup factor is 6.2. From detailed results (not given here) it can be observed that it is important to use two-dimensional buckets when both resources are similarly constraining. 

In Table 5 we give the geometric means of total solution time ratios for five variants of the algorithm applied for instances with two resources. The first four variants (O), (A), $( \mathrm { A } ^ { + } )$ , and $\left( \mathrm { A } ^ { + } \mathrm { B } \right)$ use onedimensional buckets, and the last variant $( \mathrm { A } ^ { + } \mathrm { B } )$ uses two-dimensional buckets. Again, variant (A) uses parameterization ξ 200. We provide time ratios for all 69 instances as well as for instances of different problems separately. Results in Table 5 suggest the most computational improvement is again achieved when passing from variant (O) to variant (A). Significant speedups were achieved when passing from (A) to $( \mathrm { A } ^ { + } )$ and from one-dimensional buckets to twodimensional buckets. The impact of the bucket arc elimination procedure is very small here. 


Figure 7. Performance Profiles for Variants with One-Dimensional and Two-Dimensional Buckets


![](images/f0f4a7c60fbccbb95b2253b9543e92485400c79eb0e4d4f5cde13e132f7ef18d.jpg)


## 6.5. Results for the BCP Algorithm

In this section, we present the performance of the proposed BCP algorithm on instances from the literature. We start with 74 VRPTW instances having 100 and 200 customers. We compare our algorithm with the state-of-the-art BCP algorithm for this problem by Pecin et al. (2017a). The difference between the two algorithms mainly resides in the different labeling routines included. Another difference is a better dual price smoothing stabilization with an automatically adjusted parameter (Pessoa et al. 2018) used by us. Also, we use only standard RCCs and limited-memory R1Cs with up to five rows, whereas the algorithm in Pecin et al. (2017a) uses also elementary inequalities (a family of R1Cs) with six and seven rows and clique cuts (after enumeration). Finally, different mechanisms to dynamically adjust ng-neighborhoods are employed. Other components used in both algorithms are similar. 

The computer used in Pecin et al. (2017a) and our computer have approximately the same speed, according to www.cpubenchmark.net/singleThread.html. In Table 6, we present the comparison of the two algorithms. Our algorithm outperforms the one by Pecin at al. for all classes of VRPTW instances, except class R1 for which the results are similar. Our algorithm proved optimality for five out of nine open instances with 200 customers for the first time. On the set of instances solved by both algorithms, our approach is three times faster if we compare average solution times and four times faster if we compare the geometric mean of solution times. The speedup factor on individual instances reaches 47.5 as demonstrated in the detailed results presented in the online appendix. Interestingly, the largest improvement factors are reached on instances for which the improved arc elimination procedure had the most impact. 

We have also tested our algorithm on VRPTW instances with 400 customers. In the literature only one of these instances has been solved to optimality, by Kallehauge, Larsen, and Madsen (2006). We managed to solve to optimality 24 instances out of 60. 


Table 5. Comparison of Different Variants of the Algorithm on Instances with Two Resources


<table><tr><td rowspan="2">Instances</td><td rowspan="2">Number</td><td colspan="3">One-dimensional buckets</td><td rowspan="2">One → two (A+B)</td></tr><tr><td>(O)→(A)</td><td>(A)→(A+)</td><td>(A+)→(A+B)</td></tr><tr><td>DCVRP</td><td>7</td><td>2.72</td><td>1.08</td><td>1.05</td><td>1.02</td></tr><tr><td>MDVRP</td><td>22</td><td>1.80</td><td>1.25</td><td>1.01</td><td>1.12</td></tr><tr><td>SDVRP</td><td>10</td><td>6.16</td><td>0.88</td><td>0.93</td><td>1.89</td></tr><tr><td>VRPTW</td><td>30</td><td>1.78</td><td>1.23</td><td>1.05</td><td>1.14</td></tr><tr><td>All</td><td>69</td><td>2.23</td><td>1.16</td><td>1.02</td><td>1.21</td></tr></table>

We observed three main reasons (in decreasing order of the number of impacted instances) why algorithm could not solve more 400-customer instances: (i) the labeling algorithm is not fast enough, so only relatively few R1Cs can be added before the pricing time limit is exceeded, leaving a gap that is too large; (ii) R1Cs with up to five rows can be added almost until convergence, but they still leave a gap that is too large; and (iii) column generation convergence is very slow. 

In the next experiment, we compare our algorithm with the exact algorithm by Contardo and Martinelli (2014) on the set of distance-constrained MDVRP instances. The main features of that previous algorithm that differ from ours are 

• use of standard monodirectional label-correcting algorithm for solving the pricing problem (improved by decremental state-space relaxation); 

• absence of column generation stabilization; 

• separation of different families of valid inequalities, particularly, only a subset of R1Cs is used (subset row cuts), and the limited-memory technique is not applied; and 

• exclusive use of route enumeration for finishing the instance, without any branching. 

The speed of the computer used in Contardo and Martinelli (2014) is approximately 1.6 times slower than ours, according to PassMark Software (www .cpubenchmark.net/singleThread.html). Therefore, in the following comparison, as well in the detailed results in the online appendix, the solution times of Contardo and Martinelli (2014) are divided by 1.6. In Table 7, we present a comparison of the two algorithms. Our algorithm solves all instances, most of them for the first time. When compared on the set of instances solved by both algorithms, our approach is 110 times faster in terms of the average time, and 16.5 times faster in terms of the geometric mean time (33 times faster if the smallest instance “pr01” is not taken into account). The speedup factor on individ ual instances reaches three orders of magnitude as demonstrated in the detailed results presented in the online appendix. We believe that all the differences noted above, not only the bucket graph pricing, contribute to this big improvement. For the instance “pr10,” we improved the best known solution. For other instances, the values obtained by Vidal et al. (2012) are optimal. 

In the following Table 8 we give summary results for the remaining instances. To our knowledge, these instances are treated by an exact approach for the first time. We solved to optimality six out of seven classic distance-constrained CVRP instances by Christofides, Mingozzi, and Toth (1979). All best known solution values for these instances given in Nagata and Braysy¨ (2009) are optimal. The only unsolved instance CMT13 is clustered and has many long arcs in the solution. 


Table 6. Comparison of Our BCP Algorithm with the One in Pecin et al. (2017a) on the VRPTW Instances


<table><tr><td rowspan="2">Class</td><td colspan="3">Our algorithm</td><td colspan="3">Algorithm in Pecin et al. (2017a)</td></tr><tr><td>Solved</td><td>Average time</td><td>Geomean time</td><td>Solved</td><td>Average time</td><td>Geomean time</td></tr><tr><td>Solomon hard</td><td>14/14</td><td>456</td><td>160</td><td>14/14</td><td>5,324</td><td>961</td></tr><tr><td>C1</td><td>10/10</td><td>100</td><td>14</td><td>10/10</td><td>133</td><td>61</td></tr><tr><td>C2</td><td>9/10</td><td>4,774</td><td>510</td><td>8/10</td><td>4096</td><td>3,027</td></tr><tr><td>R1</td><td>10/10</td><td>11,756</td><td>1,418</td><td>10/10</td><td>12,854</td><td>1,861</td></tr><tr><td>R2</td><td>10/10</td><td>7,248</td><td>2,158</td><td>8/10</td><td>13,626</td><td>8,991</td></tr><tr><td>RC1</td><td>8/10</td><td>15,370</td><td>6,351</td><td>8/10</td><td>38,940</td><td>13,585</td></tr><tr><td>RC2</td><td>9/10</td><td>29,970</td><td>3,751</td><td>7/10</td><td>27,372</td><td>14,390</td></tr><tr><td>Overall</td><td>70/74</td><td>9,044</td><td>690</td><td>65/74</td><td>13,066</td><td>1,956</td></tr><tr><td>Solved by both</td><td></td><td>4,350</td><td>500</td><td></td><td>13,066</td><td>1,956</td></tr></table>


Note. Geomean time, Geometric mean time. 



Table 7. Comparison of Our BCP Algorithm with the One in Contardo and Martinelli (2014) on the Distance-Constrained MDVRP Instances


<table><tr><td rowspan="2">Class</td><td colspan="3">Our algorithm</td><td colspan="3">Algorithm in Contardo and Martinelli (2014)</td></tr><tr><td>Solved</td><td>Average time</td><td>Geomean time</td><td>Solved</td><td>Average time</td><td>Geomean time</td></tr><tr><td>“p”</td><td>12/12</td><td>4,538</td><td>201</td><td>6/6</td><td>938</td><td>342</td></tr><tr><td>“pr”</td><td>10/10</td><td>13,773</td><td>937</td><td>4/7</td><td>39,058</td><td>903</td></tr><tr><td>Overall</td><td>22/22</td><td>7,881</td><td>392</td><td>10/13</td><td>16,186</td><td>504</td></tr><tr><td>Solved by both</td><td></td><td>147</td><td>30</td><td></td><td>16,186</td><td>504</td></tr></table>


Note. Geomean time, Geometric mean time. 



Table 8. Results for Instances Never Attacked by Exact Methods Before


<table><tr><td>Class</td><td>Solved</td><td>Largest solved n</td><td>Smallest unsolved n</td><td>Average time</td><td>Geomean time</td></tr><tr><td>DCVRP</td><td>6/7</td><td>199</td><td>120</td><td>5h30m</td><td>16m44s</td></tr><tr><td>SDVRP</td><td>7/10</td><td>216</td><td>240</td><td>32m09s</td><td>11m26s</td></tr><tr><td>HFVRP</td><td>56/96</td><td>186</td><td>107</td><td>3h56m</td><td>23m07s</td></tr></table>


Note. Geomean time, Geometric mean time. 


The gap for this instance is large even after adding many R1Cs. This gap can be reduced by adding even more cuts, but exact pricing becomes too expensive. 

We solved to optimality seven out of ten of distanceconstrained site-dependent instances. These instances happen to be harder than similar multidepot instances in class “pr.” The best known solution values from Cordeau and Maischberger (2012) were improved for four instances. 

Finally, we have considered HFVRP instances from Duhamel, Lacomme, and Prodhon (2011), who called these instances “nightmares.” These instances involve large capacities of vehicles, very heterogeneous customer demands, and many—up to eight—very heterogeneous vehicle types. We confirm that these instances are particularly difficult. We could not solve several instances which have slightly more than 100 customers. Nevertheless, we have solved to optimality all instances with up to 100 customers, 29 of 38 instances with 101–150 customers, and 13 of 30 instances with 151–200 customers. Improved solutions were found for 43 instances. 

The detailed results for all tested instances are given in the online appendix. 

## 7. Conclusions

This article proposed a new bucket graph–based variant of the labeling algorithm for the SPPRC. It can handle the particularities of the SPPRCs that arise as pricing subproblems in the most effective existing exact algorithms for the VRP: the partial route elementarity defined by ng-sets and the numerous additional dimensions induced by limited-memory R1Cs. The algorithm is especially better than existing variants when the consumption, in at least one of the resources, is given either by real numbers or by large integers. In adition, a new procedure that generalizes the principle of arc elimination by reduced costs to the bucket graph context was given. 

The new algorithms were embedded in an advanced BCP algorithm that includes several other features picked from the literature. The computational results were highly positive. For the classical VRPTW, the new BCP is at least four times faster on average than a very recent BCP algorithm (Pecin et al. 2017a). Moreover, the new BCP solved five additional VRPTW instances to optimality. Since most other elements in those two BCP algorithms, like ng-paths and rank-1 cuts, are similar, the significant improvement is clearly due to the techniques introduced in the paper. The experimental investigation suggests that the largest improvement comes from the organization of labels in buckets, which decreases the number of dominance checks in the pricing. In fact, this proposed labeling implementation can better handle the large numbers that define the time resource in the benchmark VRPTW instances. Other components such as dynamic adjustment of the bucket step size and an improved arc elimination procedure bring less speedup on average but can be important for some instances. Very good results were also obtained on instances from other important VRP variants, including classical DCVRP instances proposed almost 40 years ago, but only now solved to optimality. 

Although the bucket implementation presented in this paper considered up to two resources, it is not difficult to extend it for handling the SPPRCs with several resources that arise in other VRP variants. We believe that those harder problems may also benefit from the approach. However, substantial additional experimental work would be needed for determining how many and which resources should be used for defining the buckets, the remaining resources being treated as in the standard labeling. 

## Acknowledgments

Experiments presented in this paper were carried out using the PlaFRIM (Federative Platform for Research in Computer Science and Mathematics), created under the Inria Pla-FRIM development action with support from Bordeaux (l’Institut Polytechnique de Bordeaux), LaBRI (Laboratoire Bordelais de Recherche en Informatique), IMB (Institut de Mathématiques de Bordeaux), and other entities: Conseil Régional d’Aquitaine, Université de Bordeaux, CNRS (Centre National de la Recherche Scientifique), and Agence Nationale de la Recherche (ANR) in accordance with the “Programme d’Investissements d’Avenir” (see www.plafrim.fr/ en/home). 

A. Pessoa and E. Uchoa visited Bordeaux with funding from project FAPERJ E26/110.075/2014 (International Cooperation FAPERJ/INRIA). The authors thank Thibaut Vidal for running the hybrid genetic algorithm and communicating initial upper bounds for VRPTW instances with 200 and 400 customers. The authors also thank two anonymous referees and the associate editor whose comments helped to improve the quality of the paper. 

## References



Achterberg T (2007) Constraint integer programming. Unpublished doctoral dissertation, Technische Universitat, Berlin. 





Baldacci R, Mingozzi A, Roberti R (2011) New route relaxation and pricing strategies for the vehicle routing problem. Oper. Res. 59(5):1269–1283. 





Bulhoes T, Sadykov R, Uchoa E (2018) A branch-and-price algorithm for the minimum latency problem. Comput. Oper. Res. 93:66–78. 





Christofides N, Mingozzi A, Toth P (1979) The vehicle routing problem. Christofides N, Mingozzi A, Toth P, Sandi C, eds. Combinatorial Optimization (Wiley, Chichester, UK), 315–338. 





Christofides N, Mingozzi A, Toth P (1981) State-space relaxation procedures for the computation of bounds to routing problems. Networks 11(2):145–164. 





Contardo C, Martinelli R (2014) A new exact algorithm for the multi depot vehicle routing problem under capacity and route length constraints. Discrete Optim. 12:129–146. 





Contardo C, Desaulniers G, Lessard F (2015) Reaching the elementary lower bound in the vehicle routing problem with time windows. Networks 65(1):88–99. 





Cordeau JF, Laporte G (2001) A tabu search algorithm for the site dependent vehicle routing problem with time windows. Inform. Systems Oper. Res. 39(3):292–298. 





Cordeau JF, Maischberger M (2012) A parallel iterated tabu search heuristic for vehicle routing problems. Comput. Oper. Res. 39(9):2033–2050. 





Cordeau JF, Gendreau M, Laporte G (1997) A tabu search heuristic for periodic and multi-depot vehicle routing problems. Networks 30(2):105–119. 





Desaulniers G, Lessard F, Hadjar A (2008) Tabu search, partial elementarity, and generalized k-path inequalities for the vehicle routing problem with time windows. Transportation Sci. 42(3):387–404. 





Duhamel C, Lacomme P, Prodhon C (2011) Efficient frameworks for greedy split and new depth first search split procedures fo routing problems. Comput. Oper. Res. 38(4):723–739. 





Fukasawa R, Longo H, Lysgaard J, Poggi de Aragão M, Reis M, Uchoa E, Werneck R (2006) Robust branch-and-cut-and-price for the capacitated vehicle routing problem. Math. Programming 106(3):491–511. 





Gehring H, Homberger J (2002) Parallelization of a two-phase met aheuristic for routing problems with time windows. J. Heuristic 8(3):251–276. 





Irnich S, Desaulniers G (2005) Shortest path problems with resource constraints. Desaulniers G, Desrosiers J, Solomon MM, eds. Column Generation (Springer, Boston), 33–65. 





Irnich S, Desaulniers G, Desrosiers J, Hadjar A (2010) Path-reduced costs for eliminating arcs in routing and scheduling. INFORMS J. Comput. 22(2):297–313. 





Jepsen M, Petersen B, Spoorendonk S, Pisinger D (2008) Subset-row inequalities applied to the vehicle-routing problem with time windows. Oper. Res. 56(2):497–511. 





Jiang J, Ng KM, Poh KL, Teo KM (2014) Vehicle routing problem with a heterogeneous fleet and time windows. Expert System Appl. 41(8):3748–3760. 





Kallehauge B, Larsen J, Madsen OB (2006) Lagrangian duality applied to the vehicle routing problem with time windows. Comput. Oper. Res. 33(5):1464–1487. 





Laporte G, Nobert Y (1983) A branch and bound algorithm for the capacitated vehicle routing problem. OR Spectrum 5(2):77–85. 





Lozano L, Medaglia AL (2013) On an exact method for the con strained shortest path problem. Comput. Oper. Res. 40(1): 378–384. 





Lozano L, Duque D, Medaglia AL (2015) An exact algorithm for the elementary shortest path problem with resource constraints. Transportation Sci. 50(1):348–357. 





Nagata Y, Braysy O (2009) Edge assembly-based memetic algo-¨ rithm for the capacitated vehicle routing problem. Network 54(4):205–215. 





Pecin D, Contardo C, Desaulniers G, Uchoa E (2017a) New enhancements for the exact solution of the vehicle routing problem with time windows. INFORMS J. Comput. 29(3):489–502. 





Pecin D, Pessoa A, Poggi M, Uchoa E (2014) Improved branch-cutand-price for capacitated vehicle routing. Lee J, Vygen J, eds. Integer Programming and Combinatorial Optimization (Springer, Cham, Switzerland), 393–403. 





Pecin D, Pessoa A, Poggi M, Uchoa E (2017b) Improved branch-cutand-price for capacitated vehicle routing. Math. Programming Comput. 9(1):61–100. 





Pecin D, Pessoa A, Poggi M, Uchoa E, Santos H (2017c) Limited memory rank-1 cuts for vehicle routing problems. Oper. Res. Lett. 45(3):206–209. 





Penna PHV, Subramanian A, Ochi LS, Vidal T, Prins C (2019) A hybrid heuristic for a broad class of vehicle routing problems with heterogeneous fleet, Ann. Oper, Res, 273(1):5–74. 





Pessoa A, de Aragão MP, Uchoa ME (2008) Robust branch-cut-and price algorithms for vehicle routing problems. Golden B, Raghavan S, Wasil E, eds. The Vehicle Routing Problem: Latest Advances and New Challenges (Springer Science and Business Media, New York), 297–325. 





Pessoa A, Sadykov R, Uchoa E, Vanderbeck F (2018) Automation and combination of linear-programming based stabilization techniques in column generation. INFORMS J. Comput. 30(2): 339–360. 





Pessoa A, Uchoa E, de Aragão MP, Rodrigues R (2010) Exact al gorithm over an arc-time-indexed formulation for parallel machine scheduling problems. Math. Programming Comput. 2(3):259–290. 





Poggi M, Uchoa E (2014) New exact algorithms for the capacitated vehicle routing problem. Toth P, Vigo D, eds. Vehicle Routing: Problems, Methods, and Applications (SIAM, Philadelphia), 59–86. 





Pugliese LDP, Guerriero F (2013) A survey of resource constrained shortest path problems: Exact solution approaches. Networks 62(3):183–200. 





Righini G, Salani M (2006) Symmetry helps: Bounded bi-directional dynamic programming for the elementary shortest path problem with resource constraints. Discrete Optim. 3(3):255–273. 





Roberti R, Mingozzi A (2014) Dynamic ng-path relaxation for the delivery man problem. Transportation Sci. 48(3):413–424. 





Solomon MM (1987) Algorithms for the vehicle routing and scheduling problems with time window constraints. Oper. Res. 35(2): 254–265. 





Tilk C, Rothenbacher AK, Gschwind T, Irnich S (2017) Asymmetry ¨ matters: Dynamic half-way points in bidirectional labeling for solving shortest path problems with resource constraints faster. Eur. J. Oper. Res. 261(2):530–539. 





Vanderbeck F, Sadykov R, Tahiri I (2017) BaPCod—A generic branch and-price code. https://realopt.bordeaux.inria.fr/?page\_id=2. 





Vidal T, Crainic TG, Gendreau M, Prins C (2013) A hybrid genetic algorithm with adaptive diversity management for a large class of vehicle routing problems with time-windows. Comput. Oper. Res. 40(1):475–489. 





Vidal T, Crainic TG, Gendreau M, Lahrichi N, Rei W (2012) A hybrid genetic algorithm for multidepot and periodic vehicle routing problems. Oper. Res. 60(3):611–624. 

