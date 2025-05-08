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

    private void checkState(){
        if (connectionStatusLabel != null && connectionStatus.get()) {
            connectionStatusLabel.setText("Connesso");
            connectionStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else {
            connectionStatusLabel.setText("Non connesso");
            connectionStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
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
        ObservableList<Email> emails = FXCollections.observableArrayList(mails); //lista osservabile per la GUI
        if (emailListView == null) return;
        emailListView.setItems(emails);
        emailListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> { //listener di click
            if (newValue != null) {
                if (emailContentArea != null)
                    emailContentArea.setText(newValue.getTesto()); //contenuto mail
                if (!newValue.isLetta()) {
                    newValue.setLetta(true);
                    client.nowLetta(newValue);
                    emailListView.refresh(); //forza aggiornamento grafico
                }
            }
        });
        emailListView.setCellFactory(listView -> new ListCell<Email>() { //aspetto grafico di ogni riga
            @Override
            protected void updateItem(Email email, boolean empty) {
                super.updateItem(email, empty); //mantiene il comportamento da "cella"
                if (empty || email == null) { //se la mail in quella cella non esiste o è null
                    setText(null);
                    setStyle("");
                } else {
                    setText(email.toString());
                    if (!email.isLetta()) {
                        setStyle("-fx-background-color: lightblue;");  //se la mail in quella cella è non letta
                    } else {
                        setStyle(""); //se la mail in quella cella esiste ed è letta
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
        if(!connectionStatus.get()){
            showAlert("Non sei connesso al Server!");
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
        if(thereIsTheSameAccount(to)){
            showAlert("Non puoi inviare la mail due o più volte allo stesso Account!");
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
            client.sendEmail(email);
            onSendAlert("Email inviata con successo!");
            sendToField.setText("");
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
            subjectField.setText("Re: " + selectedEmail.getOggetto());
            emailBodyField.setText("");
        } else {
            showAlert("Seleziona un'email da rispondere.");
        }
    }

    @FXML
    protected void onReplyAllButtonClick() {
        Email selectedEmail = getSelectedEmail();
        if (selectedEmail != null) {
            if (helloController == null || helloController.getEmailAddressField() == null) {
                showAlert("Errore interno: Impossibile determinare l'utente corrente.");
                return;
            }
            if (sendToField == null || subjectField == null || emailBodyField == null) {
               createFields();
            }
            String currentUserEmail = helloController.getEmailAddressField().getText().trim();
            String originalSender = selectedEmail.getMittente().trim();
            Set<String> replyAllRecipientsSet = new HashSet<>();
            if (!originalSender.isEmpty() && !originalSender.equalsIgnoreCase(currentUserEmail)) {
                replyAllRecipientsSet.add(originalSender);
            }
            if (selectedEmail.getDestinatari() != null) {
                for (String recipient : selectedEmail.getDestinatari()) {
                    String trimmedRecipient = recipient.trim();
                    if (!trimmedRecipient.isEmpty() && !trimmedRecipient.equalsIgnoreCase(currentUserEmail)) {
                        replyAllRecipientsSet.add(trimmedRecipient);
                    }
                }
            }
            if (replyAllRecipientsSet.isEmpty()) {
                if (!originalSender.isEmpty()) {
                    sendToField.setText(originalSender);
                } else {
                    showAlert("Impossibile determinare i destinatari per 'Rispondi a Tutti'.");
                    sendToField.clear();
                }
            } else {
                sendToField.setText(String.join(";", replyAllRecipientsSet));
            }
            subjectField.setText("Re: " + selectedEmail.getOggetto());
            emailBodyField.setText("");
            /*if (!selectedEmail.isLetta()) {
                selectedEmail.setLetta(true);
                client.nowLetta(selectedEmail);
                emailListView.refresh();
            }*/
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
            subjectField.setText("Fwd: " + selectedEmail.getOggetto());
            emailBodyField.setText("Inoltrato da: " + selectedEmail.getMittente() + "\n\n");
        } else {
            showAlert("Seleziona un'email da inoltrare.");
        }
    }

    @FXML
    protected void onDeleteButtonClick() {
        Email selectedEmail = getSelectedEmail();
        if (selectedEmail != null) {
            if(!connectionStatus.get()){
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
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setClient(Client client) {
        this.client = client;
    }

    private void createFields() {
        sendToField = new TextField();
        sendToField.setPromptText("A:");
        subjectField = new TextField();
        subjectField.setPromptText("Oggetto:");
        emailBodyField = new TextArea();
        emailBodyField.setPromptText("Testo:");
        newEmailVBox.getChildren().addAll(sendToField, subjectField, emailBodyField);
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

    public boolean thereIsTheSameAccount(String emails) {
        String[] emailArray = emails.split("\\s*;\\s*");
        Set<String> emailSet = new HashSet<>();
        for (String email : emailArray) {
            if (!emailSet.add(email)) {
                return true;
            }
        }
        return false;
    }
}