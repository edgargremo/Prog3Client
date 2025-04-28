package com.example.prog3client.Model;
import com.example.prog3client.Controller.HelloController;
import com.example.prog3client.Controller.InboxController;
import com.example.prog3client.HelloApplication;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private Thread monitorThread;
    private boolean connectedFlag = false;
    private volatile long lastPongTimestamp = System.currentTimeMillis();
    private final ConcurrentHashMap<String, Boolean> emailCheckResults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> emailCheckLatches = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> pendingEmailChecks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isAttemptingReconnect = new AtomicBoolean(false);

    public Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
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
                //disconnect();
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

    public void receiveEmails(Inbox inbox, InboxController inboxController) {
        receiveThread = new Thread(() -> {
            try {
                String response;
                System.out.println("[DEBUG] Inizio ricezione email...");
                out.println("RICEZIONE ");
                out.flush();
                connectionMonitor(inboxController);
                while ((response = in.readLine()) != null) {
                    System.out.println("[DEBUG] Risposta ricevuta dal server: " + response);

                    if ("PONG".equals(response)) {
                        lastPongTimestamp = System.currentTimeMillis();
                        continue;
                    }

                    if ("SI!".equals(response) || "NO!".equals(response)) {
                        String email = pendingEmailChecks.poll();
                        if (email != null) {
                            boolean isValid = "SI!".equals(response);
                            emailCheckResults.put(email, isValid);
                            CountDownLatch latch = emailCheckLatches.get(email);
                            if (latch != null) {
                                latch.countDown();
                            }
                        }
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
                monitorThread.interrupt();
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

        out = null;
        in = null;
        socket = null;

        isAttemptingReconnect.set(false);
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
            while (connectedFlag && !Thread.currentThread().isInterrupted()) {
                try {
                    if (out == null || out.checkError()) {
                        throw new IOException("Errore nel PrintWriter");
                    }
                    out.println("PING ");
                    Thread.sleep(5000);

                    if (!connectedFlag) break;

                    long currentTime = System.currentTimeMillis();
                    if ((currentTime - lastPongTimestamp) >= 10000) {
                        throw new IOException("Timeout PONG");
                    }
                } catch (Exception e) {
                    System.err.println("Connessione persa: " + e.getMessage());
                    connectedFlag = false;
                    Platform.runLater(() -> inboxController.updateConnectionStatus(false));
                    attemptReconnect(inboxController); // Avvia la riconnessione
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void attemptReconnect(InboxController inboxController) {
        if (!isAttemptingReconnect.compareAndSet(false, true)) {
            return; // Evita tentativi multipli
        }

        new Thread(() -> {
            int delay = 5000; // Iniziale 5 secondi
            final int maxDelay = 60000; // Massimo 60 secondi

            while (!connectedFlag) {
                try {
                    System.out.println("Tentativo di riconnessione...");
                    Platform.runLater(() -> inboxController.updateConnectionStatus(false)); // Stato: Non connesso

                    if (connect()) {
                        System.out.println("Riconnessione riuscita.");
                        Platform.runLater(() -> inboxController.updateConnectionStatus(true)); // Stato: Connesso
                        receiveEmails(inboxController.getInbox(), inboxController); // Riavvia il thread di ricezione
                        connectionMonitor(inboxController); // Riavvia il monitor
                        break; // Esci dal ciclo di riconnessione
                    }
                } catch (IOException e) {
                    System.err.println("Riconnessione fallita: " + e.getMessage());
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // Interrompi il tentativo di riconnessione
                }

                delay = Math.min(delay * 2, maxDelay); // Incrementa il ritardo (exponential backoff)
            }

            isAttemptingReconnect.set(false); // Fine tentativi
        }).start();
    }

    public boolean checkEmail(String email) {
        CountDownLatch latch = new CountDownLatch(1);
        emailCheckLatches.put(email, latch);
        pendingEmailChecks.add(email);
        try {
            out.println("CHECK: " + email);
            boolean receivedResponse = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!receivedResponse) {
                System.err.println("[DEBUG] Timeout per la verifica dell'email: " + email);
                return false;
            }
            return emailCheckResults.getOrDefault(email, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            emailCheckLatches.remove(email);
            emailCheckResults.remove(email);
        }
    }
}