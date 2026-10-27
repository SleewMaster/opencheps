package com.opencheps.engine;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class StockfishEngine {

    public static class Pv {
        public final String uci;
        public final int cp;
        public final boolean mate;
        public final int depth;
        public Pv(String uci, int cp, boolean mate) {
            this(uci,cp,mate,0);
        }
        public Pv(String uci,int cp,boolean mate,int depth) {
            this.uci=uci;this.cp=cp;this.mate=mate;this.depth=depth;
        }
    }

    private static final String TAG = "OC/Engine";

    public interface Progress {void onUpdate(List<Pv> moves);}
    private final Object commands=new Object();
    private volatile boolean searching;
    private Process proc;
    private BufferedWriter in;
    private BufferedReader out;
    private volatile boolean ready;
    private volatile int currentMultiPv = 0;

    public synchronized void start(Context ctx) throws Exception {
        if (ready) return;
        ApplicationInfo ai = ctx.getApplicationInfo();
        File bin = new File(ai.nativeLibraryDir, "libstockfish.so");
        if (!bin.exists()) {
            throw new RuntimeException("Stockfish native lib missing at " + bin);
        }

        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(ctx.getFilesDir());
        proc = pb.start();

        in = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        out = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        send("uci");
        waitFor("uciok", 6000);
        send("setoption name Threads value "+Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()-1)));
        send("setoption name Hash value 128");
        send("setoption name Move Overhead value 100");
        send("setoption name UCI_LimitStrength value false");
        send("setoption name Ponder value false");
        setMultiPv(1);
        send("isready");
        waitFor("readyok", 6000);
        ready = true;
        Log.i(TAG, "Stockfish 18 ready: " + bin.getAbsolutePath());
    }

    private void setMultiPv(int n) throws Exception {
        if (n != currentMultiPv) {
            send("setoption name MultiPV value " + n);
            currentMultiPv = n;
        }
    }

    public synchronized List<Pv> topMoves(String fen,int time,int count) throws Exception {
        return topMoves(fen,time,count,null);
    }

    public synchronized List<Pv> topMoves(String fen,int time,int count,Progress progress) throws Exception {
        if(!ready)throw new IllegalStateException("engine not started");
        count=Math.max(1,Math.min(3,count));setMultiPv(count);
        Pv[] latest=new Pv[count+1];
        send("position fen "+fen);
        searching=true;
        long lastPublish=System.nanoTime();
        String bestMove=null;
        try {
            send("go movetime "+Math.max(300,time));
            String line;
            while((line=out.readLine())!=null) {
                if(line.startsWith("bestmove ")) {
                    String[] t=line.split("\\s+");
                    if(t.length>1&&UciInfo.isMove(t[1]))bestMove=t[1];
                    break;
                }
                UciInfo info=UciInfo.parse(line);
                if(info==null||info.index>count)continue;
                latest[info.index]=new Pv(info.move,info.cp,info.mate,info.depth);
                long now=System.nanoTime();
                if(progress!=null&&info.index==1&&info.depth>=8&&now-lastPublish>=250_000_000L) {
                    progress.onUpdate(snapshot(latest));lastPublish=now;
                }
            }
        } finally {searching=false;}
        List<Pv> result=snapshot(latest);
        // The terminal bestmove is authoritative, even if an info line was absent.
        if(bestMove!=null&&(result.isEmpty()||!bestMove.equals(result.get(0).uci))) {
            Pv found=null;
            for(Pv p:result)if(bestMove.equals(p.uci))found=p;
            if(found!=null)result.remove(found);
            result.add(0,found!=null?found:new Pv(bestMove,0,false,0));
            while(result.size()>count)result.remove(result.size()-1);
        }
        return result;
    }

    private List<Pv> snapshot(Pv[] latest) {
        List<Pv> list=new ArrayList<>();for(int i=1;i<latest.length;i++)if(latest[i]!=null)list.add(latest[i]);return list;
    }
    /** May interrupt an active search without waiting for its synchronized reader. */
    public void cancelSearch() {
        if(searching)try{send("stop");}catch(Exception ignored){}
    }

    public synchronized void stop() {
        ready = false;
        try { if (in != null) { send("stop"); send("quit"); in.close(); } } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        if (proc != null) proc.destroy();
        proc = null;
    }

    private void send(String cmd) throws Exception {
        synchronized(commands) {
            in.write(cmd);in.write("\n");in.flush();
        }
    }

    private void waitFor(String token, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while (System.currentTimeMillis() < deadline && (line = out.readLine()) != null) {
            if (line.contains(token)) return;
        }
        throw new RuntimeException("Timed out waiting for: " + token);
    }
}

