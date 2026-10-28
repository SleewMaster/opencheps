package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.widget.TextView;
import com.opencheps.camera.*;import java.io.*;

/** Exercises the production wrapper against a separate-UID Android TTS service. */
public class CameraSpeechTestActivity extends Activity {
    private CameraSpeech speech;
    private boolean sent;
    @Override public void onCreate(Bundle b){super.onCreate(b);TextView view=new TextView(this);setContentView(view);
        speech=new CameraSpeech(this,(ready,message)->{
            view.setText(message);
            if(ready&&!sent){sent=true;
                String fen="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
                String text=new MoveAnnouncement().pending(fen,"e2e4",true);
                write(speech.speak(text)?"CAMERA_SPEECH_QUEUED":"CAMERA_SPEECH_FAIL enqueue");
            }else if(!ready&&!message.startsWith("Запускаю"))write("CAMERA_SPEECH_FAIL "+message);
        });speech.start();
    }
    private void write(String result){try(FileWriter w=new FileWriter(new File(getFilesDir(),"camera-speech-result.txt"))){w.write(result);}catch(IOException e){throw new RuntimeException(e);}}
    @Override protected void onPause(){if(speech!=null)speech.stop();super.onPause();}
    @Override protected void onDestroy(){if(speech!=null)speech.close();super.onDestroy();}
}
