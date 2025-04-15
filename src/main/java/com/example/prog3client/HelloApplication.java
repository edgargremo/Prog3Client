package com.example.prog3client;

import com.example.prog3client.Controller.HelloController;
import com.example.prog3client.Controller.InboxController;
import com.example.prog3client.Model.Client;
import com.example.prog3client.Model.Inbox;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        HelloController helloController = fxmlLoader.getController();
        HelloController.setPrimaryStage(stage);
        stage.setTitle("Mail Client");
        stage.setScene(scene);
        stage.show();
        Client client = new Client("localhost", 8080);
        helloController.setClient(client);
        helloController.disableFocus();

/*
        FXMLLoader inboxLoader = new FXMLLoader(HelloApplication.class.getResource("inbox-view.fxml"));
        Scene inboxScene = new Scene(inboxLoader.load());
        InboxController inboxController = inboxLoader.getController();

// Imposta il client e i riferimenti
        inboxController.setClient(client);
        inboxController.setHelloController(helloController);
        inboxController.setInbox(new Inbox());

// Passa l'istanza corretta al controller principale
        helloController.setInboxController(inboxController);

 */
    }

    public static void main(String[] args) {
        launch(args);
    }
}