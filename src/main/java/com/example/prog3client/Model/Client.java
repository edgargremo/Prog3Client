package com.example.prog3client.Model;
import com.example.prog3client.Controller.InboxController;
import javafx.application.Platform;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Arrays;
import java.util.List;

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
            socket.setSoTimeout(10000); // tempo massimo che il socket attende
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connessione al server stabilita con successo.");
            out.println("VERIFICA:" + userEmail);
            String response = in.readLine();
            if (!"OK".equals(response)) {
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
            String testoCodificato = email.getTesto().replace("\n", "\\n").replace("£", "+-*[]");
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
                System.out.println("Inizio ricezione email / informazioni dal Server ");
                out.println("RICEZIONE ");
                out.flush();
                //connectionMonitor(inboxController);
                while ((response = in.readLine()) != null) {
                    System.out.println("Risposta ricevuta dal server: " + response);
                    if ("PONG".equals(response)) {
                        lastPongTimestamp = System.currentTimeMillis();
                        continue;
                    }
                    if ("SI!".equals(response) || "NO!".equals(response)) {
                        String email = pendingEmailChecks.poll(); //estrae la mail dalla lista
                        if (email != null) {
                            boolean isValid = "SI!".equals(response);
                            emailCheckResults.put(email, isValid); //mail valida
                            CountDownLatch latch = emailCheckLatches.get(email); //prende la campanellina della mail corrente pronto a suonarla
                            if (latch != null) {
                                latch.countDown(); //suona la campanellina
                            }
                        }
                        continue;
                    }
                    if ("VUOTA".equals(response)) {
                            out.println("VUOTA! ");
                            Platform.runLater(inboxController::updateEmailList);
                        continue;
                    }
                    if(response.startsWith("EMAIL_CANCELLATA ")){
                        String idEmail = response.substring("EMAIL_CANCELLATA ".length());
                        inbox.removeEmailById(idEmail);
                        Platform.runLater(inboxController::updateEmailList);
                    }
                    if(response.startsWith("EMAIL_LETTA ")){
                        String idEmail = response.substring("EMAIL_LETTA ".length());
                        Email email = inbox.getEmailbyId(idEmail);
                        email.setLetta(true);
                        Platform.runLater(inboxController::updateEmailList);
                    }
                    /*
                    if ("END".equals(response)) {
                        break;
                    }
                     */
                    String[] parts = response.split("£", -1);
                    if (parts.length >= 7) {
                        String id = parts[0].trim();
                        String mittente = parts[1];
                        List<String> destinatari = Arrays.asList(parts[2].split(";"));
                        String oggetto = parts[3];
                        String testoCodificato = parts[4];
                        String testoDecodificato = testoCodificato.replace("\\n", "\n").replace("+-*[]", "£");
                        String data = parts[5];
                        boolean letta = Boolean.parseBoolean(parts[6]);
                        Email email = new Email(id, mittente, destinatari, oggetto, testoDecodificato, data);
                        email.setLetta(letta);
                        if (!inbox.getEmails().stream().anyMatch(e -> e.getId().equals(id))) {
                            inbox.aggiungiEmail(email); //nessuna mail matcha l'id quindi aggiungo all'inbox
                            if (!email.isLetta()) {
                                inboxController.playNotificationSound();
                            }
                        } else { //se la mail esiste già potrebbe essere utile aggiornare lo stato di lettura
                            inbox.getEmails().stream()
                                    .filter(e -> e.getId().equals(id))
                                    .findFirst()
                                    .ifPresent(e -> e.setLetta(letta));
                        }
                        Platform.runLater(inboxController::updateEmailList); //quando può, javaFX esegue il thread di gestione della GUI
                    } else {
                        System.out.println("Formato non valido: " + parts.length);
                    }
                }
            } catch (IOException e) {
                System.err.println("Errore nella ricezione delle email: " + e.getMessage());
                if (connectedFlag) {
                    connectedFlag = false;
                    Platform.runLater(() -> inboxController.updateConnectionStatus(false));
                }
                monitorThread.interrupt();
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
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
                    //connectedFlag = false;
                    //Platform.runLater(() -> inboxController.updateConnectionStatus(false));
                    attemptReconnect(inboxController);
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void attemptReconnect(InboxController inboxController) {
        if (!isAttemptingReconnect.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            int delay = 5000;
            final int maxDelay = 20000;
            while (!connectedFlag) {
                try {
                    System.out.println("Tentativo di riconnessione ");
                    //Platform.runLater(() -> inboxController.updateConnectionStatus(false));
                    if (connect()) { //qui imposto la connessione = false per il TIMEOUT pong
                        System.out.println("Riconnessione riuscita ");
                        Platform.runLater(() -> inboxController.updateConnectionStatus(true));
                        receiveEmails(inboxController.getInbox(), inboxController);
                        connectionMonitor(inboxController);
                        break;
                    }
                } catch (IOException e) {
                    System.err.println("Riconnessione fallita: " + e.getMessage());
                }
                try {
                    Thread.sleep(delay); //dorme per tot s in base al delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                delay = Math.min(delay * 2, maxDelay);
            }
            isAttemptingReconnect.set(false);
        }).start();
    }

    public boolean checkEmail(String email) {
        CountDownLatch latch = new CountDownLatch(1); //aspetta la risposta di countdownn dal Server nel metodo receiveEmails()
        emailCheckLatches.put(email, latch); //campanello per queste mail
        pendingEmailChecks.add(email); //aggiungiamo alla coda
        try {
            out.println("CHECK: " + email);
            boolean receivedResponse = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!receivedResponse) {
                System.err.println("Timeout per la verifica dell'email: " + email);
                return false;
            }
            return emailCheckResults.getOrDefault(email, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            emailCheckLatches.remove(email); //pulisce la struttura dati
            emailCheckResults.remove(email); //pulisce la struttura dati
        }
    }
}