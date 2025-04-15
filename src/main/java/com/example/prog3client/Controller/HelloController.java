package com.example.prog3client.Controller;

import com.example.prog3client.HelloApplication;
import com.example.prog3client.Model.Client;
import com.example.prog3client.Model.Inbox;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelloController extends ControllerApp {

    @FXML
    private Label welcomeText;

    @FXML
    private Label connectionStatusLabel;

    @FXML
    private TextField emailAddressField;

    @FXML
    private VBox mainContent;

    private Client client;

    private Inbox inbox;

    private InboxController inboxController;

    public void setInboxController(InboxController inboxController) {
        this.inboxController = inboxController;
    }

    // Metodo per impostare il client
    public void setClient(Client client) {
        this.client = client;
        this.inbox = new Inbox();
    }

    // Metodo per aggiornare lo stato della connessione
    public void updateConnectionStatus(String status) {
        if (connectionStatusLabel != null) {
            connectionStatusLabel.setText("Stato: " + status);
        }
    }


    // Logica per il pulsante "Connetti"
    @FXML
    protected void onConnectButtonClick() {
        String userEmail = emailAddressField.getText();

        if (!isValidEmail(userEmail)) {
            updateConnectionStatus("Email non valida");
            return;
        }

        client.setUserEmail(userEmail);
        try {
            boolean mailEsiste = client.connect();
            if (!mailEsiste) {
                updateConnectionStatus("Email non esistente");
                return;
            }

            updateConnectionStatus("Connesso");

            // Carica la nuova scena della Inbox
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("inbox-view.fxml"));
            Parent inboxRoot = fxmlLoader.load(); // Caricamento della vista

            // Ottieni il controller della Inbox e configura i dati
            InboxController inboxController = fxmlLoader.getController();
            inboxController.setClient(client);
            inboxController.setHelloController(this);
            inboxController.setInbox(new Inbox());
            //client.receiveEmails(inbox, inboxController);

            // Cambia la scena dello Stage attuale
            Stage stage = (Stage) emailAddressField.getScene().getWindow();
            Scene scene = new Scene(inboxRoot, 600, 400);
            stage.setScene(scene);
            stage.setTitle("Inbox di "+ userEmail);
            stage.show();
            //inboxController.updateEmailList();

        } catch (IOException e) {
            updateConnectionStatus("Errore di connessione: " + e.getMessage());
        }
    }



    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }


    public void disableFocus() {
        emailAddressField.setFocusTraversable(false);
    }
    //aggiungiamo il metodo
    public TextField getEmailAddressField() {
        return emailAddressField;
    }

    public void setEmailAddressField(String email) {
        emailAddressField.setText(email);
    }

}