package groupS;


import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;

public class SushiGoHeuristic implements IStateHeuristic {

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {

        // Handle states where game has ended. Preserves current win/draw/loss evaluation.
        if (!gs.isNotTerminal()) {
            return gs.getHeuristicScore(playerId);
        }

        SGGameState state = (SGGameState) gs;

        // Player accumulated points so far
        double score = state.getGameScore(playerId);

        // Get number of tempura and sashimi already played
        int tempura = state.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Tempura).getValue();
        int sashimi = state.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Sashimi).getValue();

        int leftoverTempura = tempura % 2;
        int leftoverSashimi = sashimi % 3;

        // How many picks remain?
        int picksRemaining = state.getPlayerHands().get(playerId).getSize();

        // If we have already chosen this turn, subtract that used opportunity from hand size
        if (!state.getCardChoices().get(playerId).isEmpty()) {
            picksRemaining--;
        }

        // Assumed chance of obtaining the needed card per pick
        double q = 0.2;

        // Probability formula for at least 1 success
        double probabilityOneOrMore = 1 - Math.pow(1 - q, picksRemaining);

        // For when we have one leftover Sashimi, and require two.
        double probabilityTwoOrMore = 0;

        if (picksRemaining >= 2) {
            double probabilityZero = Math.pow(1 - q, picksRemaining);

            double probabilityExactlyOne =
                    picksRemaining * q * Math.pow(1 - q, picksRemaining - 1);

            probabilityTwoOrMore =
                    1 - probabilityZero - probabilityExactlyOne;
        }

        // Turn probability into potential points
        double potential = 0;

        if (leftoverTempura == 1) {
            potential += 5 * probabilityOneOrMore;
        }

        if (leftoverSashimi == 1) {
            potential += 10 * probabilityTwoOrMore;
        } else if (leftoverSashimi == 2) {
            potential += 10 * probabilityOneOrMore;
        }

        return (score + potential) / 50.0;
    }
}
