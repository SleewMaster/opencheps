package com.opencheps;
import com.opencheps.vision.*;import java.util.*;import java.io.*;import org.junit.Test;import static org.junit.Assert.*;
public class PieceTemplatesTest {
 @Test public void descriptorRoundTripRetainsAllPieceTypes()throws Exception{List<PieceTemplates.Entry> es=new ArrayList<>();for(char c:"PNBRQKpnbrqk".toCharArray()){float[] f=new float[800];Arrays.fill(f,.6f);es.add(new PieceTemplates.Entry(c,f));}ByteArrayOutputStream out=new ByteArrayOutputStream();new PieceTemplates(es).write(out);PieceTemplates bank=PieceTemplates.read(new ByteArrayInputStream(out.toByteArray()));assertTrue(bank.complete());assertEquals(.6f,bank.entries.get(0).feature[1],.005f);}
 @Test public void corruptedBankCannotBecomeClassifier(){for(String s:new String[]{"not a bank","OC-TEMPLATES-1\nP:no\n","OC-TEMPLATES-1\n"}){try{PieceTemplates.read(new ByteArrayInputStream(s.getBytes()));fail("accepted broken bank");}catch(IOException expected){}}}
}
