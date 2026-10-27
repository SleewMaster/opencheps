package com.opencheps.vision;

import com.opencheps.util.Side;

/**
 * Recover two consecutive moves when screen sampling missed an intermediate
 * frame (e.g. e2-e4 followed by g7-g6).  A recovery requires both intermediate
 * transitions to pass the same move validation as individually observed moves.
 *
 * Coordinates must be standard chess orientation, regardless of screen side.
 */
public final class MoveSequenceRecovery {
    private MoveSequenceRecovery() {}

    public static class Result {
        public final char[][] intermediate;
        public final MoveAnalyzer.Result first;
        public final MoveAnalyzer.Result second;

        Result(char[][] intermediate, MoveAnalyzer.Result first,
               MoveAnalyzer.Result second) {
            this.intermediate = intermediate;
            this.first = first;
            this.second = second;
        }
    }

    public static Result recover(char[][] oldBoard, char[][] newBoard, Side toMove) {
        if (oldBoard == null || newBoard == null || toMove == null
                || oldBoard.length != 8 || newBoard.length != 8) return null;
        if (!FenBuilder.validate(oldBoard).ok || !FenBuilder.validate(newBoard).ok)
            return null;

        int[] changed = new int[8];
        int count = 0;
        for (int r = 0; r < 8; r++) {
            if (oldBoard[r] == null || newBoard[r] == null
                    || oldBoard[r].length != 8 || newBoard[r].length != 8) return null;
            for (int f = 0; f < 8; f++) {
                if (oldBoard[r][f] == newBoard[r][f]) continue;
                if (count == changed.length) return null;
                changed[count++] = r * 8 + f;
            }
        }

        // Avoid guessing when there is too little evidence or too much OCR noise.
        if (count < 4 || count > 8) return null;
        Side other = toMove == Side.WHITE ? Side.BLACK : Side.WHITE;
        Result winner = null;
        int solutions = 0;
        int choices = 1 << count;
        for (int subset = 1; subset < choices - 1; subset++) {
            int firstCount = Integer.bitCount(subset);
            int secondCount = count - firstCount;
            if (firstCount < 2 || firstCount > 4
                    || secondCount < 2 || secondCount > 4) continue;

            char[][] middle = copy(oldBoard);
            for (int i = 0; i < count; i++) {
                if ((subset & (1 << i)) != 0) {
                    int rank = changed[i] / 8, file = changed[i] % 8;
                    middle[rank][file] = newBoard[rank][file];
                }
            }

            MoveAnalyzer.Result first = MoveAnalyzer.analyze(oldBoard, middle);
            if (first.mover != toMove || first.kind == MoveAnalyzer.Kind.UNKNOWN
                    || first.kind == MoveAnalyzer.Kind.NONE
                    || !FenBuilder.validate(middle).ok) continue;
            MoveAnalyzer.Result second = MoveAnalyzer.analyze(middle, newBoard);
            if (second.mover != other || second.kind == MoveAnalyzer.Kind.UNKNOWN
                    || second.kind == MoveAnalyzer.Kind.NONE) continue;

            // Multiple matching paths are ambiguous: do not show a false hint.
            if (++solutions > 1) return null;
            winner = new Result(middle, first, second);
        }
        return winner;
    }

    private static char[][] copy(char[][] input) {
        char[][] result = new char[8][8];
        for (int i = 0; i < 8; i++) {
            System.arraycopy(input[i], 0, result[i], 0, 8);
        }
        return result;
    }
}

