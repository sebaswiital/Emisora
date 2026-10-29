import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class LocutorUDP extends JFrame {
//ventanita del locutor que habla desde otro pc, igual que la del cliente pero con el micro del server.
    private JTextField txtIpServidor;
    private JToggleButton btnMicrofono;
    private JTextArea areaLogs;

    // se guarda para poder avisarle al servidor cuando nos quitamos del aire.
    private DatagramSocket socket;
    private InetAddress direccionServidor;

    private volatile boolean microfonoActivo = false;

    public LocutorUDP() {
        setTitle("Locutor UDP");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel superior con la ip del servidor y el boton del micro, igual que el panel del cliente
        JPanel panelConexion = new JPanel();
        txtIpServidor = new JTextField("localhost", 15);
        btnMicrofono = new JToggleButton("Micrófono al aire");
        panelConexion.add(new JLabel("IP servidor:"));
        panelConexion.add(txtIpServidor);
        panelConexion.add(btnMicrofono);

        areaLogs = new JTextArea();
        areaLogs.setEditable(false);

        add(panelConexion, BorderLayout.NORTH);
        add(new JScrollPane(areaLogs), BorderLayout.CENTER);

        // prender o apagar el micrófono, cada vez que se prende se lanza un hilo nuevo
        btnMicrofono.addActionListener(e -> {
            if (btnMicrofono.isSelected()) {
                txtIpServidor.setEnabled(false);
                microfonoActivo = true;
                Thread hiloMicrofono = new Thread(this::transmitirMicrofono);
                hiloMicrofono.setPriority(Thread.MAX_PRIORITY);
                hiloMicrofono.start();
            } else {
                // se bloquea hasta que el hilo del micro termine de cerrar la linea.
                btnMicrofono.setEnabled(false);
                microfonoActivo = false;
            }
        });

        // al cerrar la ventana le decimos al servidor que ya no estamos al aire, si no la canción se queda en pausa
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                microfonoActivo = false;
                desconectar();
            }
        });
    }

    private void transmitirMicrofono() {
        TargetDataLine microfono = null;
        try {
            // sin puerto fijo, el sistema nos da uno libre.
            socket = new DatagramSocket();
            direccionServidor = InetAddress.getByName(txtIpServidor.getText().trim());

            // primero intentamos con el formato de la emisora (estereo), si el micro no lo soporta usamos mono
            // y duplicamos cada muestra para que el cliente lo siga recibiendo en estereo.
            AudioFormat formatoEstandar = AudioConfig.getAudioFormat();
            AudioFormat formatoMono = new AudioFormat(formatoEstandar.getSampleRate(),
                    formatoEstandar.getSampleSizeInBits(), 1, true, formatoEstandar.isBigEndian());

            boolean esMono = !AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, formatoEstandar));
            AudioFormat formatoMicrofono = esMono ? formatoMono : formatoEstandar;

            microfono = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, formatoMicrofono));
            microfono.open(formatoMicrofono);
            microfono.start();

            // le avisamos al servidor que pause la canción y reenvie lo que le mandemos.
            enviarComando(AudioConfig.CMD_LOCUTOR_ON);
            registrarLog("Micrófono AL AIRE en " + direccionServidor.getHostAddress() + ":" + AudioConfig.PUERTO_SERVIDOR
                    + (esMono ? " (mono)." : "."));

            byte[] bufferMono = new byte[AudioConfig.TAMANO_BUFFER / 2];
            byte[] buffer = new byte[AudioConfig.TAMANO_BUFFER];

            while (microfonoActivo) {
                // read se bloquea hasta llenar el buffer, asi que el mismo micro lleva el ritmo, no hace falta sleep.
                int largo;
                if (esMono) {
                    int leidos = microfono.read(bufferMono, 0, bufferMono.length);
                    largo = monoAEstereo(bufferMono, leidos, buffer);
                } else {
                    largo = microfono.read(buffer, 0, buffer.length);
                }
                socket.send(new DatagramPacket(buffer, largo, direccionServidor, AudioConfig.PUERTO_SERVIDOR));
            }

            registrarLog("Micrófono apagado.");

        } catch (Exception e) {
            registrarLog("Error con el micrófono: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (microfono != null) {
                microfono.stop();
                microfono.close();
            }
            microfonoActivo = false;
            desconectar();
            SwingUtilities.invokeLater(() -> {
                btnMicrofono.setSelected(false);
                btnMicrofono.setEnabled(true);
                txtIpServidor.setEnabled(true);
            });
        }
    }

    // manda un mensaje de texto corto al servidor (LOCUTOR_ON o LOCUTOR_OFF).
    private void enviarComando(String comando) throws Exception {
        byte[] datos = comando.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(datos, datos.length, direccionServidor, AudioConfig.PUERTO_SERVIDOR));
    }

    private synchronized void desconectar() {
        if (socket != null && !socket.isClosed() && direccionServidor != null) {
            try {
                enviarComando(AudioConfig.CMD_LOCUTOR_OFF);
            } catch (Exception e) {
                e.printStackTrace();
            }
            socket.close();
        }
    }

    // copia cada muestra mono (2 bytes) en el canal izquierdo y derecho, devuelve los bytes escritos.
    private int monoAEstereo(byte[] mono, int largo, byte[] estereo) {
        int j = 0;
        for (int i = 0; i + 1 < largo; i += 2) {
            estereo[j++] = mono[i];
            estereo[j++] = mono[i + 1];
            estereo[j++] = mono[i];
            estereo[j++] = mono[i + 1];
        }
        return j;
    }

    // registra los mensajes en el log, como su nombre lo indica, claro que lo pone en una cola para que no vaya a hacer interferencia con nada
    private void registrarLog(String mensaje) {
        SwingUtilities.invokeLater(() -> areaLogs.append(mensaje + "\n"));
    }
//mismo que en server...
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LocutorUDP().setVisible(true));
    }
}
