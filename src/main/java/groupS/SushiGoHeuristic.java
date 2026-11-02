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


        // Account for whether you know the incoming hand from the next player
        int incomingPlayer = (playerId + 1) % state.getNPlayers();
        boolean incomingHandKnown = state.isHandKnown(playerId, incomingPlayer);
        boolean incomingHasChopsticks = state.getPlayedCardTypes()[incomingPlayer].get(SGCard.SGCardType.Chopsticks).getValue() > 0;

        int incomingTempura = 0;
        int incomingSashimi = 0;

        if (incomingHandKnown) {
            for (SGCard card : state.getPlayerHands().get(incomingPlayer).getComponents()) {
                if (card.type == SGCard.SGCardType.Tempura) {
                    incomingTempura++;
                } else if (card.type == SGCard.SGCardType.Sashimi) {
                    incomingSashimi++;
                }
            }
        }

        // If we have already chosen this turn, subtract that used opportunity from hand size
        if (!state.getCardChoices().get(playerId).isEmpty()) {
            picksRemaining--;
        }

        // Assumed chance of obtaining the needed card per pick
        double q = 0.2;

        int tempuraInHand = 0;
        int sashimiInHand = 0;

        for (SGCard card : state.getPlayerHands().get(playerId).getComponents()) {
            if (card.type == SGCard.SGCardType.Tempura) {
                tempuraInHand++;
            } else if (card.type == SGCard.SGCardType.Sashimi) {
                sashimiInHand++;
            }
        }

        boolean canChooseNow = state.getCardChoices().get(playerId).isEmpty() && picksRemaining > 0;
        boolean tempuraAvailableNow = canChooseNow && tempuraInHand > 0;
        boolean sashimiAvailableNow = canChooseNow && sashimiInHand > 0;

        // Separating current pick from later opportunities
        int laterPicks = canChooseNow ? picksRemaining - 1 : picksRemaining;

        double nextTempuraChance = q;
        double nextSashimiChance = q;
        int incomingHandSize = state.getPlayerHands().get(incomingPlayer).getSize();

        // Accounting for whether opponent is close to completing a set - meaning more likely to choose that card.
        int opponentTempura = state.getPlayedCardTypes()[incomingPlayer].get(SGCard.SGCardType.Tempura).getValue();
        int opponentSashimi = state.getPlayedCardTypes()[incomingPlayer].get(SGCard.SGCardType.Sashimi).getValue();

        boolean opponentNeedsTempura = opponentTempura % 2 == 1;
        boolean opponentNeedsSashimi = opponentSashimi % 3 == 2;

        if (laterPicks > 0 && incomingHandKnown && !incomingHasChopsticks && incomingHandSize > 1) {

            // Normal cards weight 1, tempura and sashimi completing a pair weigh 3.
            double tempuraWeight = opponentNeedsTempura ? 3.0 : 1.0;
            double sashimiWeight = opponentNeedsSashimi ? 3.0 : 1.0;
            int otherCards = incomingHandSize - incomingTempura - incomingSashimi;

            double totalWeight = (incomingTempura * tempuraWeight) + (incomingSashimi * sashimiWeight) + otherCards;

            // Chance of getting one tempura is 1 - tempura weight / total weight. If more than 1, then guaranteed. Same for Sashimi.
            nextTempuraChance = incomingTempura == 0 ? 0.0 : incomingTempura == 1 ? 1.0 - (tempuraWeight / totalWeight) : 1.0;
            nextSashimiChance = incomingSashimi == 0 ? 0.0 : incomingSashimi == 1 ? 1.0 - (sashimiWeight / totalWeight) : 1.0;
        }

        double tempuraOneLater = 0;
        double sashimiOneLater = 0;
        double sashimiTwoLater = 0;

        if (laterPicks > 0) {
            // Remaining picks - the incoming pick we're handling separately
            int remainingPicks = laterPicks - 1;

            // Chance of no matching cards
            double zeroAfterNext = Math.pow(1 - q, remainingPicks);

            double oneAfterNext = 0;
            if (remainingPicks > 0) {
                // Probability is remaining pick arrangements * q * probability that all other picks miss
                oneAfterNext = remainingPicks * q * Math.pow(1 - q, remainingPicks - 1);
            }

            // 1 - (miss next AND miss every pick afterward)
            tempuraOneLater = 1 - (1 - nextTempuraChance) * zeroAfterNext;

            sashimiOneLater = 1 - (1 - nextSashimiChance) * zeroAfterNext;

            double zeroSashimi = (1 - nextSashimiChance) * zeroAfterNext;

            double exactlyOneSashimi = nextSashimiChance * zeroAfterNext + (1 - nextSashimiChance) * oneAfterNext;

            sashimiTwoLater = 1 - zeroSashimi - exactlyOneSashimi;
        }
        // Turn probability into potential points

        // Potential from later picks, without taking either cards for now
        double tempuraLater = leftoverTempura == 1 ? 5 * tempuraOneLater : 0;

        double sashimiLater = 0;

        if (leftoverSashimi == 1) {
            sashimiLater = 10 * sashimiTwoLater;
        } else if (leftoverSashimi == 2) {
            sashimiLater = 10 * sashimiOneLater;
        }

        double potential = tempuraLater + sashimiLater;

        // or use the current pick for tempura
        if (tempuraAvailableNow && leftoverTempura == 1) {
            double takeTempura = 5 + sashimiLater;
            potential = Math.max(potential, takeTempura);
        }

        // or use the current pick for sashimi
        if (sashimiAvailableNow && leftoverSashimi > 0) {
            double sashimiAfterPick = leftoverSashimi == 2 ? 10 : 10 * sashimiOneLater;

            double takeSashimi = tempuraLater + sashimiAfterPick;
            potential = Math.max(potential, takeSashimi);
        }

        // Maki Counts
        int myMaki = state.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Maki).getValue();

        int playersAhead = 0;
        int playersTied = 0;

        for (int p = 0; p < state.getNPlayers(); p++) {
            int maki = state.getPlayedCardTypes()[p].get(SGCard.SGCardType.Maki).getValue();

            if (maki > myMaki) {
                playersAhead++;
            } else if (maki == myMaki) {
                playersTied++;
            }
        }

        if (myMaki > 0) {
            // Integer division intentionally rounds down split Maki awards
            if (playersAhead == 0) {
                potential += 6 / playersTied;
            } else if (playersAhead == 1) {
                potential += 3 / playersTied;
            }
        }

        // Pudding award estimate using current counts. Pudding rewards being first and penalises being last.
        int myPudding = state.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Pudding).getValue();

        int puddingAhead = 0;
        int puddingBehind = 0;
        int puddingTied = 0;

        for (int p = 0; p < state.getNPlayers(); p++) {
            int pudding = state.getPlayedCardTypes()[p]
                    .get(SGCard.SGCardType.Pudding).getValue();

            if (pudding > myPudding) {
                puddingAhead++;
            } else if (pudding < myPudding) {
                puddingBehind++;
            } else {
                puddingTied++;
            }
        }

        // Nobody ahead = split + 6, nobody behind = split - 6
        if (puddingTied < state.getNPlayers()) {
            if (puddingAhead == 0) {
                potential += 6 / puddingTied;
            } else if (puddingBehind == 0 && state.getNPlayers() > 2) {
                potential += -6 / puddingTied;
            }
        }

        // Unused wasabi count reward multiplier
        int unusedWasabi = state.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Wasabi).getValue();

        // Need to account for chance of Nigiri Pick, and the average value of a nigiri pick. Assumed, for now.
        double nigiriChance = 0.2;
        double averageNigiriValue = 2.0;

        double probabilityNigiri = 1 - Math.pow(1 - nigiriChance, picksRemaining);
        double wasabiBonus = (3 - 1) * averageNigiriValue;

        if (unusedWasabi > 0) {
            potential += wasabiBonus * probabilityNigiri;
        }
        return (score + potential) / 50.0;
    }
}
