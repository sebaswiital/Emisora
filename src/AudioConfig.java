import javax.sound.sampled.AudioFormat;

public class AudioConfig {

    public static AudioFormat getAudioFormat() {
        float sampleRate = 44100.0F; //Frecuencia de muestreo (44.1 kHz, calidad CD)
        int sampleSizeInBits = 16;   // resolución de audio
        int channels = 2;            // 1 para Mono  2 para estereo
        boolean signed = true;       //los datos de audio incluyen valores positivos y negativos
        boolean bigEndian = false;   //el orden en que se almacenan los bytes en la memoria

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}