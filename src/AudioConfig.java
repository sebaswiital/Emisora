import javax.sound.sampled.AudioFormat;

public class AudioConfig {

    // puerto donde el servidor escucha a los oyentes y desde donde envía el audio.
    public static final int PUERTO_SERVIDOR = 1234;
    // tamaño de cada paquete de audio, el mismo en servidor y cliente.
    public static final int TAMANO_BUFFER = 2048;
    // mensajes que manda el cliente para entrar o salir de la emisora.
    public static final String CMD_CONECTAR = "CONECTAR";
    public static final String CMD_DESCONECTAR = "DESCONECTAR";
    // mensajes que manda el locutor externo para salir o quitarse del aire.
    public static final String CMD_LOCUTOR_ON = "LOCUTOR_ON";
    public static final String CMD_LOCUTOR_OFF = "LOCUTOR_OFF";

    public static AudioFormat getAudioFormat() {
        float sampleRate = 44100.0F; //Frecuencia de muestreo (44.1 kHz, calidad CD)
        int sampleSizeInBits = 16;   // resolución de audio
        int channels = 2;            // 1 para Mono  2 para estereo
        boolean signed = true;       //los datos de audio incluyen valores positivos y negativos
        boolean bigEndian = false;   //el orden en que se almacenan los bytes en la memoria

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}
