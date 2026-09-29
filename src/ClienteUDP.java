import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ClienteUDP extends JFrame {
//jframe una ventanita sencilla para el cliente.
    private JButton btnConectar;
    private JTextArea areaLogs;

    //boton y area de texto.

    public ClienteUDP() {
        setTitle("Cliente UDP");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        btnConectar = new JButton("sintonizar al puerto");
        areaLogs = new JTextArea();
        areaLogs.setEditable(false);

        add(btnConectar, BorderLayout.NORTH);
        add(new JScrollPane(areaLogs), BorderLayout.CENTER);
        //el boton crea un hilo le da la maxima prioridad e inicia el hilo
        btnConectar.addActionListener(e -> {
            btnConectar.setEnabled(false);
            Thread hiloReceptor = new Thread(this::recibirAudio);
            hiloReceptor.setPriority(Thread.MAX_PRIORITY);
            hiloReceptor.start();
        });
    }

    private void recibirAudio() {
        registrarLog("CLIENTE");

        AudioFormat formato = AudioConfig.getAudioFormat(); // formato de el otro archivo
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

        try {
            //reservea una linea de audio
            SourceDataLine altavoces = (SourceDataLine) AudioSystem.getLine(info);
            //abre el formato especificado, adicional vamos a poner estos 32768 bites para que los altavoces no se queden en perdida de datos
            // cuando hayan retrasos.
            //edit. duplique la entrada porque aun jodía, y aun asi va a tocar ponerle un prebuffering.

            InetAddress ipAConectarse = InetAddress.getByName("192.168.10.11");
            DatagramSocket socket = new DatagramSocket(1235, ipAConectarse);

            byte[] buffer = new byte[2048];


            //abre el formato especificado, adicional vamos a poner estos 32768 bites para que los altavoces no se queden en perdida de datos
            // cuando hayan retrasos.
            //edit. duplique la entrada porque aun jodía, y aun asi va a tocar ponerle un prebuffering.
            //edit2. despues del prebuffering tampoco funcionó, lo dejo asi, no se oye mal.

            altavoces.open(formato,65536);
            // inicia la lina de reproducción.
            altavoces.start();
            registrarLog("ya se abrieron los altavoces, todo ready");



            while (true) {

                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);

                altavoces.write(paquete.getData(),0, paquete.getLength());
            }


        } catch (Exception e) {
            registrarLog("Error al transmitir el archivo:");
            e.printStackTrace();
        }
    }
    // registra los mensajes en el log, como su nombre lo indica, claro que lo pone en una cola para que no vaya a hacer interferencia con nada
    private void registrarLog(String mensaje) {
        SwingUtilities.invokeLater(() -> areaLogs.append(mensaje + "\n"));
    }
//mismo que en server...
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClienteUDP().setVisible(true));
    }
}