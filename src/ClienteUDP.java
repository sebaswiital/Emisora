import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ClienteUDP {
    public static void main(String[] args) {
        System.out.println("CLIENTE");

        AudioFormat formato = AudioConfig.getAudioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

        try {
            //reservea una linea de audio
            SourceDataLine altavoces = (SourceDataLine) AudioSystem.getLine(info);
            //abre el formato especificado, adicional vamos a poner estos 32768 bites para que los altavoces no se queden en perdida de datos
            // cuando hayan retrasos.
            altavoces.open(formato,65536);
            // inicia la lina de reproducción.
            altavoces.start();
            System.out.println("ya se abrieron los altavoces, todo ready");


            DatagramSocket socket = new DatagramSocket(1235);

            byte[] buffer = new byte[2048];

            while (true) {

                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);

                altavoces.write(paquete.getData(),0, paquete.getLength());
            }


        } catch (Exception e) {
            System.out.println("Error al transmitir el archivo:");
            e.printStackTrace();
        }
    }
}
