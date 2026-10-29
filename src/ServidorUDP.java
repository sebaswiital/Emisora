import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.sound.sampled.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorUDP extends JFrame {

    private JButton btnSeleccionar;
    private JButton btnTransmitir;
    private JToggleButton btnMicrofono;
    private JLabel lblOyentes;
    private JTextArea areaLogs;
    private File archivoSeleccionado;

    // un solo socket para escuchar a los oyentes y mandarles el audio.
    private DatagramSocket socket;
    // lista de oyentes conectados, se puede tocar desde varios hilos al tiempo.
    private final Set<InetSocketAddress> clientes = ConcurrentHashMap.newKeySet();

    // estado compartido entre el hilo de la canción y el del micrófono.
    private volatile boolean microfonoActivo = false;
    private final Object candado = new Object();

    public ServidorUDP() {
        setTitle("Servidor Transmisor UDP");
        setSize(600, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel para agrupar los botones en la parte superior
        JPanel panelBotones = new JPanel();
        btnSeleccionar = new JButton("Seleccionar Canción (.wav)");
        btnTransmitir = new JButton("Iniciar Transmisión");
        btnTransmitir.setEnabled(false); // Apagado hasta que haya canción
        btnMicrofono = new JToggleButton("Micrófono al aire");

        panelBotones.add(btnSeleccionar);
        panelBotones.add(btnTransmitir);
        panelBotones.add(btnMicrofono);

        areaLogs = new JTextArea();
        areaLogs.setEditable(false);

        lblOyentes = new JLabel("Oyentes conectados: 0");

        add(panelBotones, BorderLayout.NORTH);
        add(new JScrollPane(areaLogs), BorderLayout.CENTER);
        add(lblOyentes, BorderLayout.SOUTH);

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

        // prender o apagar el micrófono, cada vez que se prende se lanza un hilo nuevo
        btnMicrofono.addActionListener(e -> {
            if (btnMicrofono.isSelected()) {
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

        iniciarSocket();
    }

    // abre el socket en el puerto del servidor y deja un hilo escuchando a los oyentes.
    private void iniciarSocket() {
        try {
            socket = new DatagramSocket(AudioConfig.PUERTO_SERVIDOR);
            registrarLog("Emisora al aire en el puerto " + AudioConfig.PUERTO_SERVIDOR + ", esperando oyentes...");
            Thread hiloOyentes = new Thread(this::escucharClientes);
            hiloOyentes.setDaemon(true);
            hiloOyentes.start();
        } catch (IOException e) {
            registrarLog("No se pudo abrir el puerto " + AudioConfig.PUERTO_SERVIDOR + ", ¿ya hay otro servidor abierto?");
            e.printStackTrace();
            btnTransmitir.setEnabled(false);
            btnSeleccionar.setEnabled(false);
            btnMicrofono.setEnabled(false);
        }
    }

    // recibe los mensajes de CONECTAR y DESCONECTAR de los clientes.
    private void escucharClientes() {
        byte[] buffer = new byte[64];
        while (!socket.isClosed()) {
            try {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);

                String mensaje = new String(paquete.getData(), 0, paquete.getLength(), StandardCharsets.UTF_8).trim();
                InetSocketAddress cliente = (InetSocketAddress) paquete.getSocketAddress();

                if (AudioConfig.CMD_CONECTAR.equals(mensaje)) {
                    if (clientes.add(cliente)) {
                        registrarLog("Oyente conectado: " + cliente.getAddress().getHostAddress() + ":" + cliente.getPort());
                    }
                } else if (AudioConfig.CMD_DESCONECTAR.equals(mensaje)) {
                    if (clientes.remove(cliente)) {
                        registrarLog("Oyente desconectado: " + cliente.getAddress().getHostAddress() + ":" + cliente.getPort());
                    }
                }
                actualizarOyentes();
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    registrarLog("Error recibiendo de un oyente: " + e.getMessage());
                }
            }
        }
    }

    // manda el mismo paquete a todos los oyentes conectados.
    private void enviarATodos(byte[] datos, int largo) {
        for (InetSocketAddress cliente : clientes) {
            try {
                socket.send(new DatagramPacket(datos, largo, cliente));
            } catch (IOException e) {
                registrarLog("No se pudo enviar a " + cliente + ", se saca de la lista.");
                clientes.remove(cliente);
                actualizarOyentes();
            }
        }
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

            //si experimentamos con el tamaño del buffer podemos presentar variaciones en el audio, mas velocidad o perdida de calidad.
            byte[] buffer = new byte[AudioConfig.TAMANO_BUFFER];
            int bytesLeidos;

            // El bucle se acaba cuando read() devuelve -1 o sea, cuando el archivo termine.
            while ((bytesLeidos = flujoAudio.read(buffer, 0, buffer.length)) != -1) {

                if (microfonoActivo) {
                    // micro prendido: la canción se pausa aqui mismo y espera a que lo apaguen.
                    registrarLog("Canción en pausa, micrófono al aire.");
                    synchronized (candado) {
                        while (microfonoActivo) {
                            candado.wait();
                        }
                    }
                    registrarLog("Volvemos con la canción.");
                }

                enviarATodos(buffer, bytesLeidos);

                // Control de flujo para simular tiempo real
                // 44100 Hz * 2 bytes 16 bits * 2 canales = 176400 bytes por segundo.
                // 2048 bytes equivalen a aprox 11.6 milisegundos de audio.
                Thread.sleep(11);
            }

            registrarLog("se acabó.");
            flujoAudio.close();

        } catch (Exception e) { // manejo de errores.
            registrarLog("Error al transmitir el archivo:");
            e.printStackTrace();
        } finally {
            //los botones al terminar para que podamos poner otra canción.
            SwingUtilities.invokeLater(() -> {
                btnTransmitir.setEnabled(true);
                btnSeleccionar.setEnabled(true);
            });
        }
    }

    private void transmitirMicrofono() {
        TargetDataLine microfono = null;
        try {
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
            registrarLog("Micrófono AL AIRE" + (esMono ? " (mono)." : "."));

            byte[] bufferMono = new byte[AudioConfig.TAMANO_BUFFER / 2];
            byte[] buffer = new byte[AudioConfig.TAMANO_BUFFER];

            while (microfonoActivo) {
                // read se bloquea hasta llenar el buffer, asi que el mismo micro lleva el ritmo, no hace falta sleep.
                if (esMono) {
                    int leidos = microfono.read(bufferMono, 0, bufferMono.length);
                    int largo = monoAEstereo(bufferMono, leidos, buffer);
                    enviarATodos(buffer, largo);
                } else {
                    int leidos = microfono.read(buffer, 0, buffer.length);
                    enviarATodos(buffer, leidos);
                }
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
            // despertamos la canción para que siga desde donde quedó.
            synchronized (candado) {
                candado.notifyAll();
            }
            SwingUtilities.invokeLater(() -> {
                btnMicrofono.setSelected(false);
                btnMicrofono.setEnabled(true);
            });
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

    private void actualizarOyentes() {
        SwingUtilities.invokeLater(() -> lblOyentes.setText("Oyentes conectados: " + clientes.size()));
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
