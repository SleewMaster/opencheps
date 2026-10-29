package com.opencheps.vision;

import java.io.*;
import java.util.*;

/** Small, offline recognition descriptors. No screenshot or screen coordinates are stored. */
public final class PieceTemplates {
    public static final class Entry {
        public final char piece;
        public final float[] feature;
        public Entry(char piece,float[] feature){this.piece=piece;this.feature=feature.clone();}
    }
    public final List<Entry> entries;
    public PieceTemplates(List<Entry> entries){this.entries=new ArrayList<>(entries);}
    public boolean complete(){
        for(char p:"PNBRQKpnbrqk".toCharArray()){
            boolean found=false;for(Entry e:entries)if(e.piece==p)found=true;
            if(!found)return false;
        }return true;
    }
    public static PieceTemplates read(InputStream input)throws IOException {
        List<Entry> result=new ArrayList<>();
        BufferedReader r=new BufferedReader(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));
        if(!"OC-TEMPLATES-1".equals(r.readLine()))throw new IOException("Unknown descriptor format");
        String line;while((line=r.readLine())!=null){
            if(line.isEmpty())continue;
            if(result.size()>=384||line.length()>1200||line.length()<3||line.charAt(1)!=':'||"PNBRQKpnbrqk".indexOf(line.charAt(0))<0)throw new IOException("Invalid descriptor");
            byte[] bytes;try{bytes=Base64.getDecoder().decode(line.substring(2));}catch(IllegalArgumentException e){throw new IOException(e);}
            if(bytes.length!=SquareSampler.N*SquareSampler.N*2)throw new IOException("Invalid descriptor size");
            float[] feature=new float[bytes.length];for(int i=0;i<bytes.length;i++)feature[i]=(bytes[i]&255)/255f;
            result.add(new Entry(line.charAt(0),feature));
        }
        PieceTemplates bank=new PieceTemplates(result);if(!bank.complete())throw new IOException("Incomplete descriptor bank");return bank;
    }
    public void write(OutputStream output)throws IOException {
        if(!complete()||entries.size()>384)throw new IOException("Incomplete descriptor bank");
        Writer w=new OutputStreamWriter(output,java.nio.charset.StandardCharsets.UTF_8);w.write("OC-TEMPLATES-1\n");
        for(Entry e:entries){byte[] bytes=new byte[e.feature.length];for(int i=0;i<bytes.length;i++){
            if(!Float.isFinite(e.feature[i]))throw new IOException("Masked descriptor");
            bytes[i]=(byte)Math.round(Math.max(0,Math.min(1,e.feature[i]))*255);
        }w.write(e.piece+":"+Base64.getEncoder().encodeToString(bytes)+"\n");}w.flush();
    }
}
