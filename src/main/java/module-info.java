module com.example.photoelectricsensordemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires jssc;
    requires com.fazecast.jSerialComm;


    opens com.example.photoelectricsensordemo to javafx.fxml;
    exports com.example.photoelectricsensordemo;
}