package com.example.prog3client;
import com.example.prog3client.Controller.HelloController;
import com.example.prog3client.Model.Client;
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
        HelloController helloController = fxmlLoader.getController(); //gli passo un'istanza di hellocontroller
        //HelloController.setPrimaryStage(stage);
        stage.setTitle("Mail Client");
        stage.setScene(scene);
        stage.show();
        Client client = new Client("localhost", 8080); //gli passo un'istanza di Client
        helloController.setClient(client);
        helloController.disableFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}