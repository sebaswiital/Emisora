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
            //edit. duplique la entrada porque aun jodía, y aun asi va a tocar ponerle un prebuffering.

            DatagramSocket socket = new DatagramSocket(1235);

            byte[] buffer = new byte[2048];

            //pre buffering, va a recibir los 8 primeros paquetes, sin reproducirlos, es una idea. no afecta mucho la vd]
            //la mejora no es tan notoria.

            for (int i = 0; i < 8; i++) {
                DatagramPacket paqueteInicial = new DatagramPacket(buffer, buffer.length);
                socket.receive(paqueteInicial);
                altavoces.write(paqueteInicial.getData(), 0, paqueteInicial.getLength());
            }

            //abre el formato especificado, adicional vamos a poner estos 32768 bites para que los altavoces no se queden en perdida de datos
            // cuando hayan retrasos.
            //edit. duplique la entrada porque aun jodía, y aun asi va a tocar ponerle un prebuffering.

            altavoces.open(formato,65536);
            // inicia la lina de reproducción.
            altavoces.start();
            System.out.println("ya se abrieron los altavoces, todo ready");



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
