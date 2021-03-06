package players.groupAG;

import core.GameState;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Utils;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import static utils.Types.COLLAPSE_START;
import static utils.Types.COLLAPSE_STEP;

public class SingleTreeNode {
    public MCTSAGParams params;

    private SingleTreeNode parent;
    private SingleTreeNode[] children;
    private double totValue;
    private int nVisits;
    private Random m_rnd;
    private int m_depth;
    private double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private int childIdx;
    private int fmCallsCount;

    private int num_actions;
    private Types.ACTIONS[] actions;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    SingleTreeNode(MCTSAGParams p, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this(p, null, -1, rnd, num_actions, actions, 0, null);
    }

    private SingleTreeNode(MCTSAGParams p, SingleTreeNode parent, int childIdx, Random rnd, int num_actions,
                           Types.ACTIONS[] actions, int fmCallsCount, StateHeuristic sh) {
        this.params = p;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;
        children = new SingleTreeNode[num_actions];
        totValue = 0.0;
        this.childIdx = childIdx;
        if (parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        } else
            m_depth = 0;
    }

    void setRootGameState(GameState gs) {
        this.rootState = gs;
        if (params.heuristic_method == params.CUSTOM_HEURISTIC)
            this.rootStateHeuristic = new CustomHeuristic(gs);
        else if (params.heuristic_method == params.ADVANCED_HEURISTIC) // New method: combined heuristics
            this.rootStateHeuristic = new AdvancedHeuristic(gs, m_rnd);
    }


    public SingleTreeNode[] getChildren() {
        return children;
    }

    public void setM_depth(int m_depth) {
        this.m_depth = m_depth;
    }

    /**
     * Performs selection and expansion
     * For selection, if the next actions are not fully explored, explores and expands them, else uses uct for selecting(exploration and exploitation) next action
     * From the selected node, performs rollout until terminal state/ certain depth
     * backpropagates the score
     */
    void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int numIters = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while (!stop) {

            GameState state = rootState.copy();
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNode selected = treePolicy(state);
            double delta = selected.rollOut(state);
            backUp(selected, delta);

            //Stopping condition
            if (params.stop_type == params.STOP_TIME) {
                numIters++;
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (params.stop_type == params.STOP_ITERATIONS) {
                numIters++;
                stop = numIters >= params.num_iterations;
            } else if (params.stop_type == params.STOP_FMCALLS) {
                fmCallsCount += params.rollout_depth;
                stop = (fmCallsCount + params.rollout_depth) > params.num_fmcalls;
            }
        }
        //System.out.println(" ITERS " + numIters);
    }

    /**
     * If it's a terminal state or max rollout depth is reached, return current node
     * else If children of the current node are not expanded, expand them by random simulation and picking the best random value
     * Roll the state forward for the random child expanded
     * else using uct, Use exploration and exploitation to pick the next child to be explored
     */
    private SingleTreeNode treePolicy(GameState state) {

        SingleTreeNode cur = this;

        while (!state.isTerminal() && cur.m_depth < params.rollout_depth) {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);

            } else {
                cur = cur.uct(state);
            }
        }

        return cur;
    }


    private SingleTreeNode expand(GameState state) {

        int bestAction = 0;
        double bestValue = -1;

        /**
         * Of all the unexplored actions, it picks a random action
         */
        for (int i = 0; i < children.length; i++) {
            double x = m_rnd.nextDouble();
            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }

        //Roll the state
        roll(state, actions[bestAction]);

        SingleTreeNode tn = new SingleTreeNode(params, this, bestAction, this.m_rnd, num_actions,
                actions, fmCallsCount, rootStateHeuristic);
        children[bestAction] = tn;
        return tn;
    }

    /**
     * Algorithm:
     *  - For the current playerId, set it's action as act
     *  - For the opponents,
     *      - If the opponent model is same action, pick the same action as act
     *      - If the opponent model is mirror, mirror left, right, down and up actions and pick the same action for stop and bomb
     *      - If the opponent model is random, pick a random action
     * @param gs - game state
     * @param act - action to act
     */
    private void roll(GameState gs, Types.ACTIONS act) {
        //Simple, all random first, then my position.
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];
        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();

        for (int i = 0; i < nPlayers; ++i) {
            if (playerId == i) {
                actionsAll[i] = act;
            } else if (params.opp_model == MCTSAGParams.OppModel.SAME_ACTION) {
                actionsAll[i] = act;
            } else if (params.opp_model == MCTSAGParams.OppModel.MIRROR) {
                switch (act) {
                    case ACTION_UP:
                        actionsAll[i] = Types.ACTIONS.ACTION_DOWN;
                        break;
                    case ACTION_DOWN:
                        actionsAll[i] = Types.ACTIONS.ACTION_UP;
                        break;
                    case ACTION_LEFT:
                        actionsAll[i] = Types.ACTIONS.ACTION_RIGHT;
                        break;
                    case ACTION_RIGHT:
                        actionsAll[i] = Types.ACTIONS.ACTION_LEFT;
                        break;
                    default:
                        actionsAll[i] = act;
                        break;
                }
            } else {
                int actionIdx = m_rnd.nextInt(gs.nActions());
                actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
            }
        }

        gs.next(actionsAll);

    }

    /**
     * totValue = Total value for that node for all previous rollouts, In custom heuristic win = 1, loss = -1, other = heuristic value
     * child value = totValue of the child / (number of visits to the child + epsilon) -> to avoid division by zero
     * childValue = should be between double max and min value
     * uct = childValue (exploitation) + K * sqrt(log(total nVisits + 1) / (child Nvisits + eps)) (exploration)
     * pick the best value and roll state forward
     */
    private SingleTreeNode uct(GameState state) {
        SingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (SingleTreeNode child : this.children) {
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }
        if (selected == null) {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[selected.childIdx]);

        return selected;
    }

    /**
     * Performs rollouts by picking a safe random action
     */
    private double rollOut(GameState state) {
        int thisDepth = this.m_depth;

        while (!finishRollout(state, thisDepth)) {
            int action = safeRandomAction(state);
            roll(state, actions[action]);
            thisDepth++;
        }

        return rootStateHeuristic.evaluateState(state);
    }

    private int safeRandomAction(GameState state) {
        Types.TILETYPE[][] board = state.getBoard();
        ArrayList<Types.ACTIONS> actionsToTry = Types.ACTIONS.all();

        int collapsed = 0;
        int gsTick = state.getTick();

        /**
         *  Setting the number of corner tiles to avoid based on pre_collapse_steps
         */
        if (gsTick >= COLLAPSE_START - params.pre_collapse_steps) {
             collapsed = ((gsTick - COLLAPSE_START - params.pre_collapse_steps) / COLLAPSE_STEP) + 1;
        }

        int width = board.length;
        int height = board[0].length;

        while (actionsToTry.size() > 0) {

            int nAction = m_rnd.nextInt(actionsToTry.size());
            Types.ACTIONS act = actionsToTry.get(nAction);
            Vector2d dir = act.getDirection().toVec();

            Vector2d pos = state.getPosition();
            int x = pos.x + dir.x;
            int y = pos.y + dir.y;

            // avoiding the collapsed number of corners
            if (x >= collapsed && x < width - collapsed && y >= collapsed && y < height - collapsed)
                if (board[y][x] != Types.TILETYPE.FLAMES)
                    return nAction;

            actionsToTry.remove(nAction);
        }

        //Uh oh...
        // TODO remove bombing logic
        return m_rnd.nextInt(num_actions);
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean finishRollout(GameState rollerState, int depth) {
        if (depth >= params.rollout_depth)      //rollout end condition.
            return true;

        if (rollerState.isTerminal())               //end of game
            return true;

        return false;
    }

    private void backUp(SingleTreeNode node, double result) {
        SingleTreeNode n = node;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }
            n = n.parent;
        }
    }


    int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i = 0; i < children.length; i++) {

            if (children[i] != null) {
                if (first == -1)
                    first = children[i].nVisits;
                else if (first != children[i].nVisits) {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1) {
            selected = 0;
        } else if (allEqual) {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }

        return selected;
    }

    private int bestAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i = 0; i < children.length; i++) {

            if (children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + params.epsilon);
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1) {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }


    private boolean notFullyExpanded() {
        for (SingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }

    /**
     * For each node in the tree,
     *  - discount the totValue using the discount factor
     *  - set the max_depth to the level of the node in the tree
     *
     * parses the nodes in a null-safe way
     */
    public void updateTree() {
        Queue<SingleTreeNode> queue = new LinkedList<>();
        queue.offer(this);
        for (int level = 0; !queue.isEmpty(); level++) {
            int size = queue.size();
            while (size-- > 0) {
                SingleTreeNode node = queue.poll();
                if (node != null) {
                    discountNode(node);
                    node.setM_depth(level);
                    for (SingleTreeNode child : node.getChildren()) {
                        if (child != null) {
                            queue.offer(child);
                        }
                    }
                }
            }
        }
    }

    /**
     * discounts the totValue and nVisits of a node
     *
     * @param node - node in the subtree
     */
    private void discountNode(SingleTreeNode node) {
        node.nVisits = (int) (node.nVisits * params.decay_factor);
        node.totValue *= params.decay_factor;
    }
}
