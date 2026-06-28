# Imported ChatGPT Share Context

Source: https://chatgpt.com/share/6a3fed94-dee8-83ed-96e4-b66b8a625561

Imported on: 2026-06-27

## Topic

Research discussion around MSM (Move-Split-Merge) lower bounds, envelope-based lower bounds, piecewise-linear approximations of time series, and whether segment-wise lower bounds can be combined.

## MSM Graph/Dual Facts To Preserve

- The paper represents MSM through a weighted graph / edge-cover formulation.
- A self-loop weight `w(v,v)` is the generalized deletion/self-cover cost.
- For MSM in the discussed paper, the self-loop cost is `c`.
- A match edge between values `x_i` and `y_j` has weight approximately `|x_i - y_j|`, subject to the allowed warping/window edges.
- The dual variables should be denoted `d_v`, one per graph vertex.
- A valid dual assignment must satisfy:

```text
d_v >= 0
d_v <= w(v,v)          usually d_v <= c for MSM self-loops
d_u + d_v <= w(u,v)   for every allowed edge (u,v)
```

- Any lower bound of the form `sum_v d_v` is valid only if all of these dual constraints hold globally.

## Important Corrections From The Conversation

- Do not rely only on triangle inequality if the goal is to reproduce the paper's lower-bound style. Start from the dual feasibility conditions.
- A per-vertex envelope quantity is not automatically a valid lower-bound contribution. It must correspond to dual variables satisfying all edge constraints.
- Adding an upper-envelope and lower-envelope contribution for the same vertex can accidentally exceed the self-loop cap `c`; one vertex has one dual variable, not two independent contributions.
- A formula that would allow a point to contribute up to `2c` is suspect for MSM dual variables, because `d_v <= c`.
- Taking a segment-wise maximum,

```text
sum_i max(paperLB_i, myLB_i)
```

is not automatically valid. It may stitch together pieces from different feasible dual solutions and violate constraints at segment boundaries or across cross-segment edges.
- The safe global comparison is:

```text
max(sum_i paperLB_i, sum_i myLB_i)
```

provided each full summed bound comes from one globally feasible dual assignment.

## Segment Combination Criterion

Segment-wise lower bounds can be summed only under an explicit feasibility argument:

- either the graph decomposes into independent segment subgraphs with no relevant cross-segment constraints, or
- the chosen segment dual variables, after stitching, still satisfy `d_u + d_v <= w(u,v)` for every allowed global edge.

If this cannot be shown, the segment-wise maximum should be treated as an invalid or unproven bound.

## Approximation-Aware Direction

The desired direction was:

1. Approximate `X` and `Y` by piecewise-linear series `L_X` and `L_Y`.
2. Construct candidate dual variables on the approximated instance.
3. Transfer or repair those variables for the real instance.
4. Prove the repaired variables are globally dual-feasible.

A candidate repair should not be accepted just because it looks pointwise reasonable. It must be checked against every real edge:

```text
d_u + d_v <= w_real(u,v)
```

Safe generic repairs discussed:

- Global scaling: for candidate variables `tilde_d`, set

```text
lambda = min(1, min_edges w(u,v) / (tilde_d_u + tilde_d_v))
d_v = lambda * tilde_d_v
```

for edges with positive denominator. This is valid but may be weak.

- Better repair: solve the dual LP, possibly with candidate values from the paper method or approximation method as initialization/hints:

```text
maximize sum_v d_v
subject to d_v >= 0
           d_u + d_v <= w(u,v)
```

and include self-loop constraints through `d_v <= w(v,v)`.

## Code Context From The Conversation

There was also Java-oriented work around an `O(1)` helper for a sampled line

```text
y_i = a*i + b
```

and a band `[minY, maxY]` with cap `c`:

```text
sum_i min(max(0, y_i - maxY), c)
  +   min(max(0, minY - y_i), c)
```

This computes clipped excess outside a band. It is useful as a local quantity, but it is not automatically a complete MSM lower bound unless it is used to construct dual-feasible variables.

Relevant local files in this workspace likely include:

```text
src/main/java/de/umr/lambda/linearfunction/LinearFunctionUtil.java
src/main/java/de/umr/lambda/linearfunction/LinearFunctionLowerBoundUtil.java
src/test/java/LowerBoundTest.java
src/main/java/de/umr/lambda/msm/MSM.java
```

## Current Open Problem

Find a clean formulation that improves or combines the paper lower bound with the line/segment approximation lower bound while preserving one global dual-feasible assignment.

The key proof obligation is not "does each segment look valid by itself?" but:

```text
After all segment choices are combined, do the resulting d_v satisfy every global dual constraint?
```

