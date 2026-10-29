package com.opencheps.engine;

import java.util.*;

/** Freeze terminal advice for a position across screen dropouts. Never cache partial searches. */
public final class CompletedAdvice {
    private final LinkedHashMap<String,List<StockfishEngine.Pv>> entries=new LinkedHashMap<String,List<StockfishEngine.Pv>>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<StockfishEngine.Pv>> e){return size()>16;}
    };
    public List<StockfishEngine.Pv> get(String fen){List<StockfishEngine.Pv> p=entries.get(fen);return p==null?null:new ArrayList<>(p);}
    public void put(String fen,List<StockfishEngine.Pv> p){if(p==null||p.isEmpty()||entries.containsKey(fen))return;entries.put(fen,new ArrayList<>(p));}
    public void clear(){entries.clear();}
}
