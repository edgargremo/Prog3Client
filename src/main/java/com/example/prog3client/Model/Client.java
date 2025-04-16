package com.example.prog3client.Model;

import com.example.prog3client.Controller.HelloController;
import com.example.prog3client.Controller.InboxController;
import com.example.prog3client.HelloApplication;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Client {
    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String userEmail;
    private Thread receiveThread;
    private boolean connectedFlag = false;
    private volatile long lastPongTimestamp = System.currentTimeMillis();
    private Thread monitorThread;

    public Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public PrintWriter getOut() {
        return out;
    }


    public boolean isConnected() {
        return connectedFlag;
    }


    public boolean connect() throws IOException {
        try {
            System.out.println("Tentativo di connessione al server: " + serverAddress + ":" + serverPort);
            socket = new Socket(serverAddress, serverPort);
            socket.setSoTimeout(5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connessione al server stabilita con successo.");
            out.println("VERIFICA:" + userEmail);
            String response = in.readLine();
            if (!"OK".equals(response)) {
                disconnect();
                return false;
            }
            connectedFlag = true;
            return true;
        } catch (IOException e) {
            System.err.println("Errore durante la connessione al server: " + e.getMessage());
            connectedFlag = false;
            throw e;
        }
    }


    // Invia un'email al server
    public void sendEmail(Email email) {
        try {
            String testoCodificato = email.getTesto().replace("\n", "\\n");
            String request = "INVIA: " + email.getId() + "£" + email.getMittente() + "£" +
                    String.join(";", email.getDestinatari()) + "£" + email.getOggetto() + "£" + testoCodificato + "£" + email.getData() + "£" + email.isLetta();
            out.println(request);
        } catch (Exception e) {
            System.err.println("Errore nell'invio della mail: " + e.getMessage());
        }
    }

    // Riceve le email dal server
    public void receiveEmails(Inbox inbox, InboxController inboxController) {
        receiveThread = new Thread(() -> {
            try {
                String response;
                System.out.println("[DEBUG] Inizio ricezione email...");
                out.println("RICEZIONE ");
                out.flush();

                connectionMonitor(inboxController); // 👈 Avviamo il monitor qui

                while ((response = in.readLine()) != null) {
                    System.out.println("[DEBUG] Risposta ricevuta dal server: " + response);

                    if ("PONG".equals(response)) {
                        lastPongTimestamp = System.currentTimeMillis();
                        continue;
                    }

                    if ("VUOTA".equals(response)) {
                        out.println("VUOTA! ");
                        Platform.runLater(inboxController::updateEmailList);
                        continue;
                    }

                    if ("END".equals(response)) {
                        break;
                    }

                    // Gestione email
                    String[] parts = response.split("£", -1);
                    if (parts.length >= 7) {
                        String id = parts[0].trim();
                        String mittente = parts[1];
                        List<String> destinatari = Arrays.asList(parts[2].split(";"));
                        String oggetto = parts[3];

                        String testoCodificato = parts[4];
                        String testoDecodificato = testoCodificato.replace("\\n", "\n");

                        String data = parts[5];
                        boolean letta = Boolean.parseBoolean(parts[6]);

                        Email email = new Email(id, mittente, destinatari, oggetto, testoDecodificato, data);
                        email.setLetta(letta);

                        if (!inbox.getEmails().stream().anyMatch(e -> e.getId().equals(id))) {
                            inbox.aggiungiEmail(email);
                            if (!email.isLetta()) {
                                inboxController.playNotificationSound();
                            }
                        } else {
                            inbox.getEmails().stream()
                                    .filter(e -> e.getId().equals(id))
                                    .findFirst()
                                    .ifPresent(e -> e.setLetta(letta));
                        }

                        Platform.runLater(inboxController::updateEmailList);
                    } else {
                        System.out.println("[DEBUG] Formato non valido: " + parts.length);
                    }
                }
            } catch (IOException e) {
                System.err.println("Errore nella ricezione delle email: " + e.getMessage());
                if(connectedFlag){
                    connectedFlag = false;
                    Platform.runLater(() -> inboxController.updateConnectionStatus(false));
                }
                monitorThread.interrupt(); // 👈 Interrompiamo il monitor

            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }


    public void disconnect() {
        if (!connectedFlag && socket == null) return; // Già disconnesso o mai connesso

        connectedFlag = false; // Imposta il flag per primo!

        System.out.println("Disconnessione in corso..."); // Log

        // Interrompi i thread
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        if (monitorThread != null) {
            monitorThread.interrupt();
        }

        // Chiudi risorse (ignora errori qui)
        try { if (out != null) out.close(); } catch (Exception e) { /* ignore */ }
        try { if (in != null) in.close(); } catch (IOException e) { /* ignore */ }
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) { /* ignore */ }

        // Resetta riferimenti
        out = null;
        in = null;
        socket = null;
        System.out.println("Disconnessione completata."); // Log
    }


    public void nowLetta(Email email) {
        String request = ("LETTA " + email.getId());
        out.println(request);
    }

    public void deleteEmail(Email email) {
        String request = ("CANCELLA " + email.getId());
        out.println(request);
    }

    public void connectionMonitor(InboxController inboxController) {
        monitorThread = new Thread(() -> {
            // Continua solo se il flag generale è true E non siamo interrotti
            while (connectedFlag && !Thread.currentThread().isInterrupted()) {
                try {
                    if (out == null || out.checkError()) {
                        System.out.println("[Monitor] PrintWriter non valido, uscita.");
                        throw new IOException("PrintWriter error");
                    }
                    out.println("PING ");
                    // out.flush(); // PrintWriter con autoFlush=true non dovrebbe richiederlo

                    Thread.sleep(5000); // Attendi 5 secondi

                    // Controlla di nuovo il flag prima del calcolo, potrebbe essere cambiato durante sleep
                    if (!connectedFlag) break;

                    long currentTime = System.currentTimeMillis();
                    boolean timestampConnected = (currentTime - lastPongTimestamp) < 10000; // Timeout PONG 10 sec

                    if (!timestampConnected) {
                        // PONG non ricevuto in tempo
                        System.out.println("[Monitor] Timeout PONG rilevato.");
                        if (connectedFlag) { // Solo se non è già stato impostato a false da receiveEmails
                            connectedFlag = false; // Imposta il flag condiviso
                            Platform.runLater(() -> inboxController.updateConnectionStatus(false)); // Aggiorna UI
                        }
                        break; // Esci dal ciclo del monitor
                    } else {
                        // Timestamp OK. Aggiorna la UI a true SOLO se il flag generale è ancora true.
                        // Questo previene l'aggiornamento a true se receiveEmails ha appena impostato a false.
                        if (connectedFlag) {
                            Platform.runLater(() -> inboxController.updateConnectionStatus(true));
                        }
                    }

                } catch (InterruptedException e) {
                    System.out.println("[Monitor] Thread interrotto.");
                    Thread.currentThread().interrupt(); // Re-imposta lo stato interrotto
                    break; // Esci dal ciclo
                } catch (Exception e) { // Cattura errori di invio PING etc.
                    System.out.println("[Monitor] Errore: " + e.getMessage());
                    if (connectedFlag) { // Se l'errore accade mentre connessi
                        connectedFlag = false;
                        Platform.runLater(() -> inboxController.updateConnectionStatus(false));
                    }
                    break; // Esci dal ciclo in caso di errore
                }
            } // Fine while

            // Assicura che lo stato finale sia 'non connesso' quando il monitor esce
            if (connectedFlag) { // Se per qualche motivo usciamo ma il flag n                 connectedFlag = false;
                Platform.runLater(() -> inboxController.updateConnectionStatus(false));
            }
            System.out.println("[Monitor] Thread terminato.");
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

}