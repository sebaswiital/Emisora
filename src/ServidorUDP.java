import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.sound.sampled.*;
import java.awt.*;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServidorUDP extends JFrame {

    private JButton btnSeleccionar;
    private JButton btnTransmitir;
    private JTextArea areaLogs;
    private File archivoSeleccionado;

    public ServidorUDP() {
        setTitle("Servidor Transmisor UDP");
        setSize(500, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel para agrupar los botones en la parte superior
        JPanel panelBotones = new JPanel();
        btnSeleccionar = new JButton("Seleccionar Canción (.wav)");
        btnTransmitir = new JButton("Iniciar Transmisión");
        btnTransmitir.setEnabled(false); // Apagado hasta que haya canción

        panelBotones.add(btnSeleccionar);
        panelBotones.add(btnTransmitir);

        areaLogs = new JTextArea();
        areaLogs.setEditable(false);

        add(panelBotones, BorderLayout.NORTH);
        add(new JScrollPane(areaLogs), BorderLayout.CENTER);

        // Logica del selector de archivos
        btnSeleccionar.addActionListener(e -> {
            JFileChooser selector = new JFileChooser();
            FileNameExtensionFilter filtro = new FileNameExtensionFilter("Archivos de Audio WAV", "wav");
            selector.setFileFilter(filtro);

            int resultado = selector.showOpenDialog(this);

            if (resultado == JFileChooser.APPROVE_OPTION) {
                archivoSeleccionado = selector.getSelectedFile();
                registrarLog("Canción cargada: " + archivoSeleccionado.getName());
                btnTransmitir.setEnabled(true);
            }
        });

        // Lanzamiento de la transmisión en un hilo independiente
        btnTransmitir.addActionListener(e -> {
            if (archivoSeleccionado != null) {
                btnTransmitir.setEnabled(false);
                btnSeleccionar.setEnabled(false);
                Thread hiloTransmisor = new Thread(this::transmitirAudio);
                hiloTransmisor.setPriority(Thread.MAX_PRIORITY);
                hiloTransmisor.start();
            }
        });
    }

    private void transmitirAudio() {
        registrarLog("SERVIDOR");
        try {
            // Usamos el archivo de la interfaz en lugar de la ruta estática
            AudioInputStream flujoAudioOriginal = AudioSystem.getAudioInputStream(archivoSeleccionado);

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

            registrarLog("se acabó.");
            socket.close();
            flujoAudio.close();

            //los botones al terminar para que podamos poner otra canción.
            SwingUtilities.invokeLater(() -> {
                btnTransmitir.setEnabled(true);
                btnSeleccionar.setEnabled(true);
            });

        } catch (Exception e) { // manejo de errores.
            registrarLog("Error al transmitir el archivo:");
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                btnTransmitir.setEnabled(true);
                btnSeleccionar.setEnabled(true);
            });
        }
    }
// registra los mensajes en el log, como su nombre lo indica, claro que lo pone en una cola para que no vaya a hacer interferencia con nada
    private void registrarLog(String mensaje) {
        SwingUtilities.invokeLater(() -> areaLogs.append(mensaje + "\n"));
    }
//comando de inicialización, hace visible la ventana y manteniendo el invoke later por lo mismo de lo anterior.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServidorUDP().setVisible(true));
    }
}