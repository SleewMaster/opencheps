package com.opencheps.vision;

import android.graphics.Bitmap;
import android.util.Log;

import com.opencheps.util.Side;

public class PieceClassifier {

    private static final String TAG = "OC/Classifier";

    private static final char[] START_WHITE_POV = {
        'r','n','b','q','k','b','n','r',
        'p','p','p','p','p','p','p','p',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        '.','.','.','.','.','.','.','.',
        'P','P','P','P','P','P','P','P',
        'R','N','B','Q','K','B','N','R'
    };

    private PixelMask annotationMask=PixelMask.NONE;
    private boolean cameraMode;
    public synchronized void setCameraMode(boolean enabled){cameraMode=enabled;}
    public synchronized void setAnnotationMask(PixelMask mask){annotationMask=mask==null?PixelMask.NONE:mask;}
    private SquareSampler.Sample[] templates;
    private float[][] normalizedTemplates;
    private SquareSampler.Sample[] previousFrame;
    private char[] previousLabels;
    private char[] labels;
    private float emptyEdgeMax;
    private float occupiedEdgeMin;
    private float pieceColorThreshold;
    private float frameChangeThreshold;
    private float whitePieceLumAvg;
    private float blackPieceLumAvg;
    private boolean trained;
    private Side bootstrapSide;

    public boolean isTrained() { return trained; }
    public Side bootstrapSide() { return bootstrapSide; }
    public synchronized void setBoardPerspective(Side side){if(trained)bootstrapSide=side;}

    public synchronized boolean tryBootstrap(Bitmap bmp, BoardDetector.Board board) {
        if (board == null) return false;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return false;

        float maxEmptyEdge = 0;
        int emptyCount = 0;
        for (int r = 2; r <= 5; r++) for (int f = 0; f < 8; f++) {
            float e = samples[r * 8 + f].edgeEnergy;
            if (e > maxEmptyEdge) maxEmptyEdge = e;
            emptyCount++;
        }

        float minOccupiedEdge = Float.MAX_VALUE;
        int occCount = 0;
        for (int r = 0; r < 8; r++) {
            if (r >= 2 && r <= 5) continue;
            for (int f = 0; f < 8; f++) {
                float e = samples[r * 8 + f].edgeEnergy;
                if (e < minOccupiedEdge) minOccupiedEdge = e;
                occCount++;
            }
        }

        if (minOccupiedEdge < .005f || minOccupiedEdge < maxEmptyEdge * 1.5f) {
            Log.i(TAG, "bootstrap reject: minOccupied=" + minOccupiedEdge
                    + " maxEmpty=" + maxEmptyEdge);
            return false;
        }

        emptyEdgeMax = maxEmptyEdge;
        occupiedEdgeMin = minOccupiedEdge;
        pieceColorThreshold = (maxEmptyEdge + minOccupiedEdge) * 0.5f;

        float topLumSum = 0, botLumSum = 0;
        int topN = 0, botN = 0;
        for (int f = 0; f < 8; f++) {
            for (int r : new int[]{0, 1}) {
                topLumSum += samples[r * 8 + f].meanLum;
                topN++;
            }
            for (int r : new int[]{6, 7}) {
                botLumSum += samples[r * 8 + f].meanLum;
                botN++;
            }
        }
        if (topN == 0 || botN == 0) return false;
        float topAvg = topLumSum / topN;
        float botAvg = botLumSum / botN;
        Side side = botAvg > topAvg ? Side.WHITE : Side.BLACK;

        char[] map = side == Side.WHITE ? START_WHITE_POV : flipLabels(START_WHITE_POV);
        normalizedTemplates=null;
        templates = samples;
        labels = map;
        bootstrapSide = side;

        float whiteSum = 0; int whiteN = 0;
        float blackSum = 0; int blackN = 0;
        for (int i = 0; i < 64; i++) {
            char c = labels[i];
            if (c == '.') continue;
            float lum = samples[i].meanLum;
            if (Character.isUpperCase(c)) { whiteSum += lum; whiteN++; }
            else                          { blackSum += lum; blackN++; }
        }
        whitePieceLumAvg = whiteN > 0 ? whiteSum / whiteN : 0.7f;
        blackPieceLumAvg = blackN > 0 ? blackSum / blackN : 0.3f;

        previousFrame = samples;
        previousLabels = new char[64];
        for (int i = 0; i < 64; i++) previousLabels[i] = labels[i];

        float maxBootstrapNoise = 0;
        for (int i = 0; i < 64; i++) {
            if (labels[i] != '.') continue;
            for (int j = i + 1; j < 64; j++) {
                if (labels[j] != '.') continue;
                if ((((i/8)+(i%8))&1) != (((j/8)+(j%8))&1)) continue;
                float d = SquareSampler.lumDistance(samples[i].feature, samples[j].feature);
                if (d > maxBootstrapNoise) maxBootstrapNoise = d;
            }
        }
        frameChangeThreshold = Math.max(0.3f, maxBootstrapNoise * 2.0f + 0.1f);

        trained = true;
        Log.i(TAG, "BOOTSTRAPPED side=" + side
                + " emptyMax=" + maxEmptyEdge
                + " occMin=" + minOccupiedEdge
                + " thr=" + pieceColorThreshold
                + " frameChangeThr=" + frameChangeThreshold);
        return true;
    }

    public synchronized char[][] classifyBoard(Bitmap bmp, BoardDetector.Board board) {
        if (!trained) return null;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return null;

        char[][] grid = new char[8][8];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int idx = r * 8 + f;
                if (previousFrame != null && previousLabels != null) {
                    float fd = SquareSampler.lumDistance(
                            samples[idx].feature, previousFrame[idx].feature);
                    if (fd < frameChangeThreshold) {
                        grid[r][f] = previousLabels[idx];
                        continue;
                    }
                }
                grid[r][f] = classifyOne(samples[idx], r, f);
            }
        }
        return grid;
    }

    public synchronized void commitConfirmed(char[][] grid, Bitmap bmp, BoardDetector.Board board) {
        if (!trained || grid == null) return;
        SquareSampler.Sample[] samples = sampleAll(bmp, board);
        if (samples == null) return;
        previousFrame = samples;
        if (previousLabels == null) previousLabels = new char[64];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                previousLabels[r * 8 + f] = grid[r][f] == 0 ? '.' : grid[r][f];
            }
        }
    }

    private SquareSampler.Sample[] sampleAll(Bitmap bmp, BoardDetector.Board board) {
        if (board.square < 8) return null;
        int rad = Math.max(4, (int) (board.square * 0.42f));
        SquareSampler.Sample[] samples = new SquareSampler.Sample[64];
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int cx = Math.round(board.left + (f + 0.5f) * board.square);
                int cy = Math.round(board.top  + (r + 0.5f) * board.square);
                samples[r * 8 + f] = cameraMode?SquareSampler.sampleCamera(bmp,cx,cy,rad,annotationMask):SquareSampler.sample(bmp, cx, cy, rad, annotationMask);
            }
        }
        return samples;
    }

    private char[] flipLabels(char[] src) {
        char[] o = new char[64];
        for (int r = 0; r < 8; r++)
            for (int f = 0; f < 8; f++)
                o[r * 8 + f] = src[(7 - r) * 8 + (7 - f)];
        return o;
    }

    private char classifyOne(SquareSampler.Sample s, int r, int f) {
        if (s.edgeEnergy < pieceColorThreshold) return '.';

        float distWhite = Math.abs(s.meanLum - whitePieceLumAvg);
        float distBlack = Math.abs(s.meanLum - blackPieceLumAvg);
        boolean isWhitePiece = distWhite < distBlack;

        boolean light = ((r + f) & 1) == 0;
        float best = Float.MAX_VALUE;
        char bestC = isWhitePiece ? 'P' : 'p';
        for (int i = 0; i < templates.length; i++) {
            char tc = labels[i];
            if (tc == '.') continue;
            if (Character.isUpperCase(tc) != isWhitePiece) continue;
            boolean tplLight = (((i / 8) + (i % 8)) & 1) == 0;
            if (tplLight != light) continue;
            float d = SquareSampler.lumDistance(s.feature, templates[i].feature);
            if (d < best) { best = d; bestC = tc; }
        }
        return bestC;
    }

    /** Costs for all 13 labels, without guessing the piece type first. */
    public synchronized float[][] labelCosts(Bitmap bmp, BoardDetector.Board board) {
        if(!trained)return null;
        SquareSampler.Sample[] samples=sampleAll(bmp,board);
        if(samples==null)return null;
        float[][] costs=new float[64][VisualMoveTracker.PIECES.length()];
        for(int i=0;i<64;i++) {
            float[] feature=normalizedTemplates==null?samples[i].feature:normalizeContrast(samples[i].feature);
            float empty=SquareSampler.lumDistance(feature,new float[feature.length]);
            costs[i][0]=empty/SquareSampler.N/SquareSampler.N;
            for(int j=1;j<VisualMoveTracker.PIECES.length();j++) {
                char wanted=VisualMoveTracker.PIECES.charAt(j);float best=Float.MAX_VALUE;
                for(int k=0;k<labels.length;k++)if(labels[k]==wanted) {
                    float d=SquareSampler.lumDistance(feature,normalizedTemplates==null?templates[k].feature:normalizedTemplates[k])/SquareSampler.N/SquareSampler.N;
                    if(d<best)best=d;
                }
                costs[i][j]=best;
            }
        }
        return costs;
    }

    public synchronized float[][] snapshotFeatures(Bitmap bmp, BoardDetector.Board board) {
        SquareSampler.Sample[] samples=sampleAll(bmp,board);if(samples==null)return null;
        float[][] result=new float[64][];for(int i=0;i<64;i++)result[i]=samples[i].feature.clone();return result;
    }
    public static PieceTemplates withObserved(PieceTemplates bank,char[][] screen,float[][] features) {
        java.util.List<PieceTemplates.Entry> all=new java.util.ArrayList<>(bank.entries.subList(0,Math.min(320,bank.entries.size())));
        if(features!=null)for(int i=0;i<64;i++)if(screen[i/8][i%8]!='.'){
            boolean valid=true;for(float v:features[i])if(!Float.isFinite(v))valid=false;
            if(valid)all.add(new PieceTemplates.Entry(screen[i/8][i%8],features[i]));
        }
        return new PieceTemplates(all);
    }

    /** Install an offline bank without assuming where any piece is currently standing. */
    public synchronized void useTemplates(PieceTemplates bank, Side perspective) {
        templates=new SquareSampler.Sample[bank.entries.size()];labels=new char[templates.length];normalizedTemplates=new float[templates.length][];
        for(int i=0;i<templates.length;i++){
            PieceTemplates.Entry e=bank.entries.get(i);labels[i]=e.piece;
            templates[i]=new SquareSampler.Sample(e.feature.clone(),0,0,0,0,0);
            normalizedTemplates[i]=normalizeContrast(e.feature);
        }
        previousFrame=null;previousLabels=null;bootstrapSide=perspective;trained=bank.complete();
    }
    /** Preserve the foreground luminance while compensating for faint piece/background contrast. */
    public static float[] normalizeContrast(float[] feature) {
        float peak=0;for(int i=0;i+1<feature.length;i+=2){float ink=feature[i]+feature[i+1];if(Float.isFinite(ink))peak=Math.max(peak,ink);}
        float gain=Math.min(5f,1f/Math.max(.2f,peak));float[] out=feature.clone();
        for(int i=0;i<out.length;i++)out[i]*=gain;return out;
    }
    public synchronized PieceTemplates exportTemplates() {
        java.util.List<PieceTemplates.Entry> entries=new java.util.ArrayList<>();
        if(trained)for(int i=0;i<labels.length;i++)if(labels[i]!='.')entries.add(new PieceTemplates.Entry(labels[i],templates[i].feature));
        return new PieceTemplates(entries);
    }

    public synchronized void reset() {
        templates = null;normalizedTemplates=null;
        labels = null;
        previousFrame = null;
        previousLabels = null;
        trained = false;
        bootstrapSide = null;
    }
}

