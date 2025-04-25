package com.example.prog3client.Controller;

import com.example.prog3client.Model.Client;
import com.example.prog3client.Model.Email;
import com.example.prog3client.Model.Inbox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InboxController {

    @FXML
    private ListView<Email> emailListView;

    @FXML
    private TextArea emailContentArea;

    @FXML
    private TextField subjectField;

    @FXML
    private Label connectionStatusLabel;

    @FXML
    private BooleanProperty connectionStatus = new SimpleBooleanProperty(true);

    @FXML
    private TextArea emailBodyField;

    private Client client;

    @FXML
    private TextField sendToField;

    @FXML
    private VBox newEmailVBox;

    @FXML
    private Button sendButton;

    private Inbox inbox;

    private HelloController helloController;

    public void setInbox(Inbox inbox) {
        this.inbox = inbox;
        client.connectionMonitor(this);
        checkState();
        connectionStatus.addListener(observable -> checkState());
        updateEmailList();
        client.receiveEmails(inbox, this);
    }
    //modifica perchè posso usare client.isconnect
    private void checkState(){
        if (connectionStatusLabel != null && connectionStatus.get() == true) {
            connectionStatusLabel.setText("Connesso");
            connectionStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else {
            connectionStatusLabel.setText("Non connesso");
            connectionStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            //System.err.println("Attenzione: connectionStatusLabel non è stato iniettato correttamente dall'FXML.");
        }
    }

    public Inbox getInbox() {
        return inbox;
    }


    public void setHelloController(HelloController helloController) {
        this.helloController = helloController;
    }

    public void updateEmailList() {
        if (inbox == null) return;

        List<Email> mails = inbox.getEmails();
        Collections.reverse(mails);
        ObservableList<Email> emails = FXCollections.observableArrayList(mails);

        if (emailListView == null) return;

        emailListView.setItems(emails);

        emailListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (emailContentArea != null)
                    emailContentArea.setText(newValue.getTesto());
                // 👉 Marca come letta e aggiorna la vista
                if (!newValue.isLetta()) {
                    newValue.setLetta(true);
                    client.nowLetta(newValue);
                    emailListView.refresh(); // aggiorna lo sfondo nella cell factory
                }
            }
        });

        // 👉 Imposta la CellFactory qui se non è già stata impostata altrove
        emailListView.setCellFactory(listView -> new ListCell<Email>() {
            @Override
            protected void updateItem(Email email, boolean empty) {
                super.updateItem(email, empty);
                if (empty || email == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(email.toString());
                    if (!email.isLetta()) {
                        setStyle("-fx-background-color: lightblue;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
    }


    public Email getSelectedEmail() {
        if (emailListView != null) {
            return emailListView.getSelectionModel().getSelectedItem();
        }
        return null;
    }

    @FXML
    protected void onSendButtonClick() {
        if (sendToField == null || subjectField == null || emailBodyField == null) {
            createFields();
            return;
        }
        String to = sendToField.getText();
        String subject = subjectField.getText();
        String body = emailBodyField.getText();

        if (to.isEmpty() || subject.isEmpty() || body.isEmpty()) {
            showAlert("Tutti i campi sono obbligatori!");
            return;
        }

        if(helloController == null){
            showAlert("Errore nella connessione!");
            return;
        }

        if(!AreValidEmails(to) || !AreEmailsExisting(to)){
            showAlert("Uno o più destinatari non validi!");
            return;
        }

        Email email = new Email(
                UUID.randomUUID().toString(),
                helloController.getEmailAddressField().getText(),
                List.of(to),
                subject,
                body,
                dateConverter()
        );
        try{
            if(connectionStatus.get() == false){
                showAlert("Non sei connesso al Server!");
                return;
            }
            client.sendEmail(email);
            onSendAlert("Email inviata con successo!");sendToField.setText("");
            subjectField.setText("");
            emailBodyField.setText("");
        } catch (Exception e) {
            showAlert("Errore nell'invio della mail");
        }
    }

    @FXML
    protected void onReplyButtonClick() {
        Email selectedEmail = getSelectedEmail();
        if (selectedEmail != null) {
            if (sendToField == null || subjectField == null || emailBodyField == null) {
                createFields();
            }
            sendToField.setText(selectedEmail.getMittente().trim());
            subjectField.setText("Re: ");
            emailBodyField.setText("");
        } else {
            showAlert("Seleziona un'email da rispondere.");
        }
    }

    @FXML
    protected void onReplyAllButtonClick() {
        Email selectedEmail = getSelectedEmail();
        if (selectedEmail != null) {
            // Assumiamo per ora che i campi siano iniettati correttamente e createFields() non serva
            // if (sendToField == null || subjectField == null || emailBodyField == null) {
            //     createFields();
            // }

            // Verifica dipendenze essenziali
            if (helloController == null || helloController.getEmailAddressField() == null) {
                showAlert("Errore interno: Impossibile determinare l'utente corrente.");
                return;
            }

            if (sendToField == null || subjectField == null || emailBodyField == null) {
               createFields();
            }


            // --- CORREZIONE LOGICA DESTINATARI ---
            String currentUserEmail = helloController.getEmailAddressField().getText().trim();
            String originalSender = selectedEmail.getMittente().trim();

            // Usiamo un Set per gestire unicità ed esclusioni
            Set<String> replyAllRecipientsSet = new HashSet<>();

            // 1. Aggiungi il mittente originale (se non è l'utente corrente)
            if (!originalSender.isEmpty() && !originalSender.equalsIgnoreCase(currentUserEmail)) {
                replyAllRecipientsSet.add(originalSender);
            }

            // 2. Aggiungi tutti i destinatari originali (escludendo l'utente corrente)
            if (selectedEmail.getDestinatari() != null) {
                for (String recipient : selectedEmail.getDestinatari()) {
                    String trimmedRecipient = recipient.trim();
                    // Aggiungi solo se non è vuoto e non è l'utente corrente
                    if (!trimmedRecipient.isEmpty() && !trimmedRecipient.equalsIgnoreCase(currentUserEmail)) {
                        replyAllRecipientsSet.add(trimmedRecipient);
                    }
                }
            }

            // 3. Imposta il campo "A:"
            if (replyAllRecipientsSet.isEmpty()) {
                // Se non ci sono altri destinatari, rispondi solo al mittente originale
                if (!originalSender.isEmpty()) {
                    sendToField.setText(originalSender);
                } else {
                    // Caso strano: email senza mittente o solo a se stesso?
                    showAlert("Impossibile determinare i destinatari per 'Rispondi a Tutti'.");
                    sendToField.clear();
                }
            } else {
                // Unisci gli indirizzi unici trovati con ";"
                sendToField.setText(String.join(";", replyAllRecipientsSet));
            }
            // --- FINE CORREZIONE LOGICA DESTINATARI ---


            // Manteniamo per ora il resto del codice problematico per affrontarlo dopo
            subjectField.setText("Re: " + selectedEmail.getOggetto());
            emailBodyField.setText(""); // Problema: corpo vuoto

            // Problema: marcatura come letta inappropriata
            if (!selectedEmail.isLetta()) {
                selectedEmail.setLetta(true);
                client.nowLetta(selectedEmail);
                emailListView.refresh(); // Aggiorna la vista
            }
        } else {
            showAlert("Seleziona un'email da rispondere.");
        }
    }

    @FXML
    protected void onForwardButtonClick() {
        Email selectedEmail = getSelectedEmail();
        if (selectedEmail != null) {
            if (sendToField == null || subjectField == null || emailBodyField == null) {
                createFields();
            }
            sendToField.setText("");
            subjectField.setText("Fwd: ");
            emailBodyField.setText("Inoltrato da:" + selectedEmail.getMittente() + "\n\n");
        } else {
            showAlert("Seleziona un'email da inoltrare.");
        }
    }

    @FXML
    protected void onDeleteButtonClick() {
        Email selectedEmail = getSelectedEmail();
        if (selectedEmail != null) {
            if(connectionStatus.get() == false){
                showAlert("Non sei connesso al Server!");
                return;
            }
            client.deleteEmail(selectedEmail);
            inbox.rimuoviEmail(selectedEmail);
            emailContentArea.setText("");
            updateEmailList();
        } else {
            showAlert("Seleziona un'email da cancellare.");
        }
    }

    private void showAlert(String message) {
        playErrorSound();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void onSendAlert(String message) {
        //suono
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setClient(Client client) {
        this.client = client;
    }

    private void createFields() {
        //creiamo i campi
        sendToField = new TextField();
        sendToField.setPromptText("A:");
        subjectField = new TextField();
        subjectField.setPromptText("Oggetto:");
        emailBodyField = new TextArea();
        emailBodyField.setPromptText("Testo:");
        //aggiungiamo i campi al vbox
        newEmailVBox.getChildren().addAll(sendToField, subjectField, emailBodyField);
    }

    public void enableSendButton() {
        if (sendButton != null) {
            sendButton.setDisable(false);
        }
    }

    private String dateConverter() {
        LocalDateTime ldt = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return ldt.format(formatter);
    }

    public void playNotificationSound() {
        try {
            URL soundURL = getClass().getResource("/com/example/prog3client/Sounds/notify.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundURL);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playErrorSound(){
        try {
            URL soundURL = getClass().getResource("/com/example/prog3client/Sounds/error.wav");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundURL);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateConnectionStatus(boolean stato){
            connectionStatus.set(stato);
    }

    private boolean AreValidEmails(String email) {
        String emailRegex = "^([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})(\\s*;\\s*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})*$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    public boolean AreEmailsExisting(String emails){
        String[] stringOfMail = emails.split("\\s*;\\s*");
        for (String email : stringOfMail) {
            if(email.isEmpty()) return false;
            else{
                if(!client.checkEmail(email)) return false;
            }
        }
        return true;
    }
}