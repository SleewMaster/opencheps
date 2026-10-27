package com.opencheps.vision;

import com.opencheps.util.Side;
import java.util.ArrayList;
import java.util.List;

/** Checks that two board snapshots describe a geometrically plausible single move.
 * Input must be in standard orientation: black pieces at the top of the array.
 * Does not replace a chess engine's check/king-safety validation. */
public class MoveAnalyzer {
    public enum Kind { NONE, QUIET, CAPTURE, CASTLE, EN_PASSANT, PROMOTION, UNKNOWN }

    public static class Result {
        public final Kind kind;
        public final Side mover;
        public final int changeCount;
        public Result(Kind kind, Side mover, int changeCount) {
            this.kind = kind;
            this.mover = mover;
            this.changeCount = changeCount;
        }
    }

    private static Result unknown(int changes) {
        return new Result(Kind.UNKNOWN, null, changes);
    }

    private static boolean white(char piece) { return Character.isUpperCase(piece); }
    private static boolean empty(char c) { return c == '.' || c == 0; }

    public static Result analyze(char[][] prev, char[][] curr) {
        if (!validGrid(prev) || !validGrid(curr)) return unknown(0);

        List<int[]> removed = new ArrayList<>();
        List<int[]> added = new ArrayList<>();
        List<int[]> replaced = new ArrayList<>();
        int changes = 0;
        for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) {
            char p = prev[r][f], c = curr[r][f];
            if (empty(p)) p = '.';
            if (empty(c)) c = '.';
            if (p == c) continue;
            changes++;
            if (p != '.' && c == '.') removed.add(new int[]{r, f, p});
            else if (p == '.' && c != '.') added.add(new int[]{r, f, c});
            else replaced.add(new int[]{r, f, p, c});
        }
        if (changes == 0) return new Result(Kind.NONE, null, 0);

        if (removed.size() == 1 && added.size() == 1 && replaced.isEmpty()) {
            int[] from = removed.get(0), to = added.get(0);
            char piece = (char) from[2], arrived = (char) to[2];
            if (white(piece) != white(arrived)
                    || !validArrival(piece, arrived, to[0])
                    || !validGeometry(prev, from[0], from[1], to[0], to[1], false))
                return unknown(changes);
            Kind kind = piece != arrived ? Kind.PROMOTION : Kind.QUIET;
            return new Result(kind, white(piece) ? Side.WHITE : Side.BLACK, changes);
        }

        if (removed.size() == 1 && replaced.size() == 1 && added.isEmpty()) {
            int[] from = removed.get(0), to = replaced.get(0);
            char piece = (char) from[2], victim = (char) to[2], arrived = (char) to[3];
            if (white(piece) == white(victim) || white(piece) != white(arrived)
                    || Character.toLowerCase(victim) == 'k'
                    || !validArrival(piece, arrived, to[0])
                    || !validGeometry(prev, from[0], from[1], to[0], to[1], true))
                return unknown(changes);
            Kind kind = piece != arrived ? Kind.PROMOTION : Kind.CAPTURE;
            return new Result(kind, white(piece) ? Side.WHITE : Side.BLACK, changes);
        }

        if (removed.size() == 2 && added.size() == 1 && replaced.isEmpty()) {
            int[] to = added.get(0);
            char arrived = (char) to[2];
            if (Character.toLowerCase(arrived) != 'p') return unknown(changes);
            int dir = white(arrived) ? -1 : 1;
            int expectedFromRow = white(arrived) ? 3 : 4;
            for (int i = 0; i < 2; i++) {
                int[] from = removed.get(i), captured = removed.get(1 - i);
                if ((char) from[2] == arrived
                        && Character.toLowerCase((char) captured[2]) == 'p'
                        && white((char) captured[2]) != white(arrived)
                        && from[0] == expectedFromRow
                        && to[0] == from[0] + dir && Math.abs(to[1] - from[1]) == 1
                        && captured[0] == from[0] && captured[1] == to[1]) {
                    return new Result(Kind.EN_PASSANT,
                            white(arrived) ? Side.WHITE : Side.BLACK, changes);
                }
            }
            return unknown(changes);
        }

        if (removed.size() == 2 && added.size() == 2 && replaced.isEmpty()) {
            for (Side side : Side.values()) {
                int row = side == Side.WHITE ? 7 : 0;
                char king = side == Side.WHITE ? 'K' : 'k';
                char rook = side == Side.WHITE ? 'R' : 'r';
                if (castle(prev, curr, row, king, rook, 7, 6, 5)
                        || castle(prev, curr, row, king, rook, 0, 2, 3)) {
                    return new Result(Kind.CASTLE, side, changes);
                }
            }
        }
        return unknown(changes);
    }

    private static boolean validGrid(char[][] g) {
        if (g == null || g.length != 8) return false;
        for (char[] rank : g) if (rank == null || rank.length != 8) return false;
        return true;
    }

    private static boolean castle(char[][] old, char[][] next, int row, char king,
                                  char rook, int rookStart, int kingEnd, int rookEnd) {
        if (old[row][4] != king || old[row][rookStart] != rook
                || next[row][4] != '.' || next[row][rookStart] != '.'
                || next[row][kingEnd] != king || next[row][rookEnd] != rook)
            return false;
        int lo = Math.min(4, rookStart), hi = Math.max(4, rookStart);
        for (int file = lo + 1; file < hi; file++) {
            if (!empty(old[row][file])) return false;
        }
        return true;
    }

    private static boolean validArrival(char from, char to, int rank) {
        boolean pawn = Character.toLowerCase(from) == 'p';
        boolean lastRank = white(from) ? rank == 0 : rank == 7;
        if (!pawn) return from == to;
        if (!lastRank) return from == to;
        char p = Character.toLowerCase(to);
        return p == 'q' || p == 'r' || p == 'b' || p == 'n';
    }

    private static boolean validGeometry(char[][] old, int fr, int ff, int tr,
                                         int tf, boolean capture) {
        if (fr == tr && ff == tf) return false;
        char p = Character.toLowerCase(old[fr][ff]);
        int dr = tr - fr, df = tf - ff;
        int ar = Math.abs(dr), af = Math.abs(df);
        switch (p) {
            case 'p':
                int dir = white(old[fr][ff]) ? -1 : 1;
                if (capture) return dr == dir && af == 1;
                if (df != 0) return false;
                if (dr == dir) return true;
                int start = dir == -1 ? 6 : 1;
                return fr == start && dr == 2 * dir && empty(old[fr + dir][ff]);
            case 'n': return ar == 1 && af == 2 || ar == 2 && af == 1;
            case 'b': return ar == af && pathClear(old, fr, ff, tr, tf);
            case 'r': return (ar == 0 || af == 0) && pathClear(old, fr, ff, tr, tf);
            case 'q': return (ar == af || ar == 0 || af == 0)
                                  && pathClear(old, fr, ff, tr, tf);
            case 'k': return ar <= 1 && af <= 1;
            default: return false;
        }
    }

    private static boolean pathClear(char[][] board, int fr, int ff, int tr, int tf) {
        int dr = Integer.compare(tr, fr), df = Integer.compare(tf, ff);
        int r = fr + dr, f = ff + df;
        while (r != tr || f != tf) {
            if (!empty(board[r][f])) return false;
            r += dr;
            f += df;
        }
        return true;
    }
}
