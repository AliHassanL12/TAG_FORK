package groupS;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.List;
import java.util.Random;

import java.util.Map;
import java.util.HashMap;

import players.PlayerConstants;
import utilities.ElapsedCpuTimer;

/**
 * This is a simple version of MCTS that may be useful for newcomers to TAG and MCTS-like algorithms
 * It strips out some of the additional configuration of MCTSPlayer. It uses BasicTreeNode in place of
 * SingleTreeNode.
 */
public class BasicMCTSPlayer extends AbstractPlayer {

    public BasicMCTSPlayer() {
        this(System.currentTimeMillis());
    }

    public BasicMCTSPlayer(long seed) {
        super(new BasicMCTSParams(), "Basic MCTS");
        // for clarity we create a new set of parameters here, but we could just use the default parameters
        parameters.setRandomSeed(seed);
        rnd = new Random(seed);

        // These parameters can be changed, and will impact the Basic MCTS algorithm
        BasicMCTSParams params = getParameters();
        params.K = Math.sqrt(2);
        params.rolloutLength = 10;
        params.maxTreeDepth = 5;
        params.epsilon = 1e-6;

        setName("Group S MCTS");

    }

    public BasicMCTSPlayer(BasicMCTSParams params) {
        super(params, "Basic MCTS");
        rnd = new Random(params.getRandomSeed());

        setName("MCTS " + params.heuristic.getClass().getSimpleName());
    }


    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> actions) {

        BasicMCTSParams params = getParameters();
        int samples = Math.max(1, params.determinizations);

        // If configured for one deal, run original search
        if (samples == 1) {
            BasicTreeNode root = new BasicTreeNode(this, null, gameState, rnd);
            root.mctsSearch();
            return root.bestAction();
        }

        boolean timed = params.budgetType == PlayerConstants.BUDGET_TIME;

        // for timed budgets, give configured allowance
        ElapsedCpuTimer timer = new ElapsedCpuTimer();
        if (timed) {
            timer.setMaxTimeMillis(params.budget);
        }

        Map<AbstractAction, Integer> totals = new HashMap<>();

        // Avoid requesting more trees than budget can support
        if (!timed) {
            samples = Math.min(samples, Math.max(1, params.budget));
        }


        // Create each sampled tree
        for (int i = 0; i < samples; i++) {

            // Reserving at least 2 ms for finishing up
            if (timed && timer.remainingTimeMillis() <= Math.max(2, params.breakMS)) {
                break;
            }

            // First tree uses supplied deal, whereas other trees resample unknown information
            AbstractGameState sampledState = i == 0 ? gameState.copy() : gameState.copy(getPlayerID());

            BasicTreeNode root = new BasicTreeNode(this, null, sampledState, rnd);

            int searchBudget;

            if (timed) {
                long usableTime = timer.remainingTimeMillis() - Math.max(2, params.breakMS);

                searchBudget = (int) (usableTime / (samples - i));

                if (searchBudget <= 0) {
                    break;
                }
            } else {
                // Divide the count equally and then distribute remainder
                searchBudget = params.budget / samples;
                if (i < params.budget % samples) {
                    searchBudget++;
                }
            }

            // search and collect results
            root.mctsSearch(searchBudget);
            addRootVisits(totals, root);
        }

        // Legal fallback if no search completed
        AbstractAction bestAction = actions.get(rnd.nextInt(actions.size()));
        int bestVisits = -1;

        // Check every legal action supplied by the game
        for (AbstractAction action : actions) {
            int visits = totals.getOrDefault(action, 0);

            if (visits > bestVisits) {
                bestVisits = visits;
                bestAction = action;
            } else if (visits == bestVisits && rnd.nextBoolean()) { // if tied, flip a coin
                bestAction = action;
            }
        }

        return bestAction;
    }

    @Override
    public BasicMCTSParams getParameters() {
        return (BasicMCTSParams) parameters;
    }

    public void setStateHeuristic(IStateHeuristic heuristic) {
        getParameters().setParameterValue("heuristic", heuristic);
        getParameters().heuristic = heuristic;
    }


    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public BasicMCTSPlayer copy() {
        BasicMCTSPlayer retValue = new BasicMCTSPlayer((BasicMCTSParams) parameters.copy());
        retValue.setName(toString());
        return retValue;
    }

    // add root visits to the total scoreboard of current actions
    private void addRootVisits(Map<AbstractAction, Integer> totals, BasicTreeNode root) {

        for (Map.Entry<AbstractAction, Integer> entry : root.getRootActionVisits().entrySet()) {

            AbstractAction action = entry.getKey();
            int visits = entry.getValue();

            int previousTotal = totals.getOrDefault(action, 0);
            totals.put(action, previousTotal + visits);
        }
    }
}