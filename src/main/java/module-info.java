module com.example.prog3client {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;


    opens com.example.prog3client to javafx.fxml;
    exports com.example.prog3client;
    exports com.example.prog3client.Controller;
    exports com.example.prog3client.Utils;
    exports com.example.prog3client.Model;
    opens com.example.prog3client.Controller to javafx.fxml;
}