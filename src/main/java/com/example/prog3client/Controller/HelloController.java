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

public class HelloController {

    @FXML
    private Label connectionStatusLabel;

    @FXML
    private TextField emailAddressField;

    private Client client;

    public void setClient(Client client) {
        this.client = client;
        //this.inbox = new Inbox();
    }

    public void updateConnectionStatus(String status) {
        if (connectionStatusLabel != null) {
            connectionStatusLabel.setText("Stato: " + status);
        }
    }

    @FXML
    protected void onConnectButtonClick() {
        String userEmail = emailAddressField.getText();
        if (!isValidEmail(userEmail)) {
            updateConnectionStatus("Email non valida");
            return;
        }
        client.setUserEmail(userEmail);
        try {
            if (!client.connect()) {
                updateConnectionStatus("Email non esistente");
                return;
            }
            updateConnectionStatus("Connesso");
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("inbox-view.fxml"));
            Parent inboxRoot = fxmlLoader.load();
            InboxController inboxController = fxmlLoader.getController();
            inboxController.setClient(client);
            inboxController.setHelloController(this);
            inboxController.setInbox(new Inbox());
            Stage stage = (Stage) emailAddressField.getScene().getWindow();
            Scene scene = new Scene(inboxRoot, 600, 400);
            stage.setScene(scene);
            stage.setTitle("Inbox di "+ userEmail);
            stage.show();
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

    public TextField getEmailAddressField() {
        return emailAddressField;
    }
}