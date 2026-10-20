package players.heuristics;

import core.AbstractGameState;
import core.CoreConstants;
import core.interfaces.IStateHeuristic;
import games.cantstop.CantStopGameState;

import java.util.List;

import games.cantstop.CantStopParameters;
import utilities.Utils;

public class CantStopHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        CantStopGameState cs = (CantStopGameState) gs;
        int currentPlayer = cs.getCurrentPlayer();
        double gameScore = cs.getGameScore(playerId);
        CoreConstants.GameResult game_status = cs.getGameStatus();
        if (cs.isNotTerminal()){
            double tempMarkerPos;
            int markerPos;
            double advance = 0;
            CantStopParameters params = (CantStopParameters) cs.getGameParameters();
            List<Integer> markers_moved = cs.getMarkersMoved();
            for (int i = 2; i <= 12; i++) {
                double columnEnd = params.maxValue(i);
                double pos = cs.getMarkerPosition(i, playerId);
                advance += (pos / columnEnd); // will be up to 11.0
            }
            for (Integer integer : markers_moved) {
                tempMarkerPos = cs.getTemporaryMarkerPosition(integer);
                int columnEnd = params.maxValue(integer);
                markerPos = cs.getMarkerPosition(integer, currentPlayer);
                double markerPosGain = tempMarkerPos - markerPos;

                if (currentPlayer == playerId) advance += (markerPosGain / columnEnd);
                else advance -= (markerPosGain / columnEnd);

            }
            return (advance + gameScore) / 16.0 ; // 11 from all the cols, 3 from temp markers, and 2 from game score
        }
        return switch (cs.getPlayerResults()[playerId]) {
            case DRAW_GAME, TIMEOUT, GAME_ONGOING -> 0.0;
            case WIN_GAME -> 1.0;
            case LOSE_GAME, GAME_END, DISQUALIFY -> -1.0;
        };
    }
}
