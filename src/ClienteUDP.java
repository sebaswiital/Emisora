import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class ClienteUDP extends JFrame {
//jframe una ventanita sencilla para el cliente.
    private JButton btnConectar;
    private JTextField txtIpServidor;
    private JTextArea areaLogs;

    //boton, campo para la ip del servidor y area de texto.

    // se guarda para poder avisarle al servidor cuando cerramos la ventana.
    private DatagramSocket socket;
    private InetAddress direccionServidor;

    public ClienteUDP() {
        setTitle("Cliente UDP");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel superior con la ip del servidor y el boton, igual que el panel del server
        JPanel panelConexion = new JPanel();
        txtIpServidor = new JTextField("localhost", 15);
        btnConectar = new JButton("sintonizar al puerto");
        panelConexion.add(new JLabel("IP servidor:"));
        panelConexion.add(txtIpServidor);
        panelConexion.add(btnConectar);

        areaLogs = new JTextArea();
        areaLogs.setEditable(false);

        add(panelConexion, BorderLayout.NORTH);
        add(new JScrollPane(areaLogs), BorderLayout.CENTER);
        //el boton crea un hilo le da la maxima prioridad e inicia el hilo
        btnConectar.addActionListener(e -> {
            btnConectar.setEnabled(false);
            txtIpServidor.setEnabled(false);
            Thread hiloReceptor = new Thread(this::recibirAudio);
            hiloReceptor.setPriority(Thread.MAX_PRIORITY);
            hiloReceptor.start();
        });

        // al cerrar la ventana le decimos al servidor que ya no nos mande mas audio
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }
        });
    }

    private void recibirAudio() {
        registrarLog("CLIENTE");

        AudioFormat formato = AudioConfig.getAudioFormat(); // formato de el otro archivo
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);

        try {
            //reservea una linea de audio
            SourceDataLine altavoces = (SourceDataLine) AudioSystem.getLine(info);

            // sin puerto fijo, el sistema nos da uno libre, asi pueden haber varios clientes en el mismo pc.
            socket = new DatagramSocket();
            byte[] buffer = new byte[AudioConfig.TAMANO_BUFFER];

            // nos registramos en la emisora, el servidor nos manda el audio al puerto desde donde le hablamos.
            direccionServidor = InetAddress.getByName(txtIpServidor.getText().trim());
            enviarComando(AudioConfig.CMD_CONECTAR);
            registrarLog("Conectado a " + direccionServidor.getHostAddress() + ":" + AudioConfig.PUERTO_SERVIDOR);

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
            registrarLog("Error al recibir el audio: " + e.getMessage());
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                btnConectar.setEnabled(true);
                txtIpServidor.setEnabled(true);
            });
        }
    }

    // manda un mensaje de texto corto al servidor (CONECTAR o DESCONECTAR).
    private void enviarComando(String comando) throws Exception {
        byte[] datos = comando.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(datos, datos.length, direccionServidor, AudioConfig.PUERTO_SERVIDOR));
    }

    private void desconectar() {
        if (socket != null && !socket.isClosed() && direccionServidor != null) {
            try {
                enviarComando(AudioConfig.CMD_DESCONECTAR);
            } catch (Exception e) {
                e.printStackTrace();
            }
            socket.close();
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
