package com.opencheps.touchprobe;
import android.speech.tts.*;import android.media.AudioFormat;
import java.util.*;import java.io.*;

/** CI-only engine: records the actual Android TTS request and returns a short silent PCM buffer. */
public class TestSpeechService extends TextToSpeechService {
    @Override protected String[] onGetLanguage(){return new String[]{"rus","RUS",""};}
    @Override protected int onIsLanguageAvailable(String language,String country,String variant){return "rus".equals(language)||"ru".equals(language)?TextToSpeech.LANG_COUNTRY_AVAILABLE:TextToSpeech.LANG_NOT_SUPPORTED;}
    @Override protected int onLoadLanguage(String language,String country,String variant){return onIsLanguageAvailable(language,country,variant);}
    @Override public List<Voice> onGetVoices(){return Collections.singletonList(new Voice("test-ru",new Locale("ru","RU"),Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,Collections.emptySet()));}
    @Override public int onIsValidVoiceName(String name){return "test-ru".equals(name)?TextToSpeech.SUCCESS:TextToSpeech.ERROR;}
    @Override public int onLoadVoice(String name){return onIsValidVoiceName(name);}
    @Override public String onGetDefaultVoiceNameFor(String lang,String country,String variant){return "test-ru";}
    @Override protected void onStop(){android.util.Log.i("OC/TestSpeech","STOP");}
    @Override protected void onSynthesizeText(SynthesisRequest request,SynthesisCallback callback){
        String text=request.getCharSequenceText().toString();
        try(FileWriter writer=new FileWriter(new File(getFilesDir(),"spoken.txt"))){writer.write(text);}catch(IOException e){throw new RuntimeException(e);}
        android.util.Log.i("OC/TestSpeech","SPOKEN "+text);
        callback.start(16000,AudioFormat.ENCODING_PCM_16BIT,1);byte[] silence=new byte[1600];callback.audioAvailable(silence,0,silence.length);callback.done();
    }
}
