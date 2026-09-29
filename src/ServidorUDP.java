import javax.sound.sampled.*;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class ServidorUDP {
    public static void main(String[] args) throws Exception {

        System.out.println("SERVIDOR");
        try {

            String ruta = "C:/Users/Jhon Perdomo/Downloads/Daft-Punk-Digital-Love-_Official-Audio_-Daft-Punk-_128k_.wav";
            File archivoAudio = new File(ruta);
            AudioInputStream flujoAudioOriginal = AudioSystem.getAudioInputStream(archivoAudio);

            // convierte el formato del archivo al formato estándar de audioconfig
            // como se que este el archivo se va a transmitir como diga audioconfig
            AudioFormat formatoEstandar = AudioConfig.getAudioFormat();
            AudioInputStream flujoAudio = AudioSystem.getAudioInputStream(formatoEstandar, flujoAudioOriginal);



            // Configuración del Socket UDP
            DatagramSocket socket = new DatagramSocket();
            InetAddress direccionCliente = InetAddress.getByName("localhost");
            int puertoCliente = 1235;

            //si experimentamos con el tamaño del buffer podemos presentar variaciones en el audio, mas velocidad o perdida de calidad.
            byte[] buffer = new byte[2048];
            int bytesLeidos;

            // El bucle se acaba cuando read() devuelve -1 o sea, cuando el archivo termine.
            while ((bytesLeidos = flujoAudio.read(buffer, 0, buffer.length)) != -1) {

                DatagramPacket paquete = new DatagramPacket(buffer, bytesLeidos, direccionCliente, puertoCliente);
                socket.send(paquete);

                // Control de flujo para simular tiempo real
                // 44100 Hz * 2 bytes 16 bits * 1 canal = 88200 bytes por segundo.
                // 1024 bytes equivalen a aprox 11.6 milisegundos de audio.
                Thread.sleep(11);
            }

            System.out.println("se acabó.");
            socket.close();
            flujoAudio.close();

        } catch (Exception e) {
            System.out.println("Error al transmitir el archivo:");
            e.printStackTrace();
        }

    }
}
