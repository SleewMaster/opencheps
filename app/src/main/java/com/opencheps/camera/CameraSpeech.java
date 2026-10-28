package com.opencheps.camera;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.*;

/** Device TTS only; no network voices or downloads are requested. Calls belong to the UI thread. */
public final class CameraSpeech {
    public interface Listener { void changed(boolean ready,String message); }
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready,closed;
    private int generation,utterance;
    private String speakingId="";
    public CameraSpeech(Context context,Listener listener){this.context=context.getApplicationContext();this.listener=listener;}
    public boolean ready(){return ready&&!closed;}
    public void start(){
        if(closed)return;
        final int token=++generation;ready=false;speakingId="";
        if(tts!=null){tts.shutdown();tts=null;}
        listener.changed(false,"Запускаю голос…");
        try{tts=new TextToSpeech(context,result->main.post(()->initialize(token,result)));}
        catch(RuntimeException e){unavailable("Голос недоступен");}
    }
    private void initialize(int token,int result){
        if(closed||token!=generation||tts==null)return;
        try{
            if(result!=TextToSpeech.SUCCESS){unavailable("Нет движка озвучки");return;}
            Set<Voice> available=tts.getVoices();List<Voice> voices=new ArrayList<>();
            if(available!=null)for(Voice voice:available){
                if("ru".equals(voice.getLocale().getLanguage())&&!voice.isNetworkConnectionRequired()
                        &&(voice.getFeatures()==null||!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)))voices.add(voice);
            }
            voices.sort(Comparator.comparingInt(Voice::getQuality).reversed().thenComparing(Voice::getName));
            boolean selected=false;for(Voice voice:voices)if(tts.setVoice(voice)==TextToSpeech.SUCCESS){selected=true;break;}
            if(!selected){unavailable("Нужен русский голос офлайн");return;}
            tts.setSpeechRate(.95f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String id){}
                @Override public void onDone(String id){main.post(()->{if(id.equals(speakingId))speakingId="";});}
                @Override public void onError(String id){main.post(()->{if(!closed&&token==generation&&id.equals(speakingId)){speakingId="";unavailable("Ошибка озвучки · проверь русский голос");}});}
            });
            ready=true;listener.changed(true,"Голос готов");
        }catch(RuntimeException e){unavailable("Голос недоступен");}
    }
    private void unavailable(String message){ready=false;listener.changed(false,message);}
    public boolean speak(String text){
        if(!ready()||text==null||text.isEmpty())return false;
        try{
            String id="move-"+(++utterance);
            if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,id)==TextToSpeech.SUCCESS){speakingId=id;return true;}
        }catch(RuntimeException ignored){}
        unavailable("Ошибка озвучки · проверь русский голос");return false;
    }
    /** True only when a queued/playing phrase was interrupted, so it may be retried after re-verification. */
    public boolean stop(){boolean interrupted=!speakingId.isEmpty();speakingId="";if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}return interrupted;}
    public void close(){closed=true;ready=false;generation++;if(tts!=null){tts.shutdown();tts=null;}main.removeCallbacksAndMessages(null);}
}
