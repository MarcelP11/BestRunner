package com.example.photoelectricsensordemo;



import com.fazecast.jSerialComm.*;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Comparator;


public class HelloApplication extends Application {


    private boolean isRunning = false;
    private Timeline timeline;
    private int elapsedSeconds = 0;
    private int elapsedMillis = 0;
    private Label timerLabel;
    private Label statusLabel;
    private Circle connectionIndicator;
    private TableView<ObservableList<String>> tableView;

    private TextField nameField;
    private TextField teamField;

    private String currentName;
    private String currentTeam;

    private int disableStopSeconds = 0;
    private boolean allowStop = true;
    private boolean readyForSensorStart = false;

    private SerialPort chosenPort;

    private boolean soundEnabled = true;
    private boolean sensorStartEnabled = false;
    private MediaPlayer mediaPlayer;

    @Override
    public void start(Stage primaryStage) {
        // Zvukový súbor
        Media sound = new Media(Paths.get("src/main/resources/sound/beep.mp3").toUri().toString());
        mediaPlayer = new MediaPlayer(sound);

        statusLabel = new Label("Timer is not running");
        statusLabel.setTextFill(Color.RED);
        statusLabel.setStyle("-fx-font-size: 2em;");

        timerLabel = new Label("Elapsed time: 00:00:00.00");
        timerLabel.setStyle("-fx-font-size: 2em;");

        connectionIndicator = new Circle(7.5, Color.RED);
        connectionIndicator.setStroke(Color.BLACK);
        connectionIndicator.setStrokeWidth(1);

        Label connectionLabel = new Label("Connection with sensor:");
        HBox connectionBox = new HBox(5, connectionLabel, connectionIndicator);
        connectionBox.setAlignment(Pos.CENTER_RIGHT);

        Label soundLabel = new Label("Beep after stop:");
        RadioButton soundOn = new RadioButton("On");
        RadioButton soundOff = new RadioButton("Off");
        ToggleGroup soundGroup = new ToggleGroup();
        soundOn.setToggleGroup(soundGroup);
        soundOff.setToggleGroup(soundGroup);
        soundOn.setSelected(soundEnabled);

        soundGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                soundEnabled = newToggle == soundOn;
            }
        });

        HBox soundBox = new HBox(5, soundLabel, soundOn, soundOff);
        soundBox.setAlignment(Pos.CENTER_RIGHT);

        Label sensorLabel = new Label("Start with sensor:");
        RadioButton sensorOn = new RadioButton("On");
        RadioButton sensorOff = new RadioButton("Off");
        ToggleGroup sensorGroup = new ToggleGroup();
        sensorOn.setToggleGroup(sensorGroup);
        sensorOff.setToggleGroup(sensorGroup);
        sensorOff.setSelected(!sensorStartEnabled);

        // Inicializácia tlačidiel Ready a Cancel
        Button readyButton = new Button("Ready");
        readyButton.setPrefWidth(100);
        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(100);

        readyButton.setDisable(!sensorStartEnabled);
        cancelButton.setDisable(true);

        sensorGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                sensorStartEnabled = newToggle == sensorOn;
                readyButton.setDisable(!sensorStartEnabled);
                cancelButton.setDisable(true);
            }
        });

        HBox sensorBox = new HBox(5, sensorLabel, sensorOn, sensorOff);
        sensorBox.setAlignment(Pos.CENTER_RIGHT);

        Button startButton = new Button("Start");
        startButton.setPrefWidth(100);
        Button stopButton = new Button("Stop");
        stopButton.setPrefWidth(100);
        Button deleteButton = new Button("Delete");
        Button exportButton = new Button("Export as CSV");

        readyButton.setOnAction(event -> {
            readyForSensorStart = true;
            readyButton.setDisable(true);
            cancelButton.setDisable(false);
        });

        cancelButton.setOnAction(event -> {
            readyForSensorStart = false;
            readyButton.setDisable(false);
            cancelButton.setDisable(true);
        });

        nameField = new TextField();
        nameField.setPromptText("Enter player name");
        teamField = new TextField();
        teamField.setPromptText("Enter team");

        Spinner<Integer> disableStopSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 60, 0));
        disableStopSpinner.setEditable(true);

        startButton.setOnAction(event -> {
            if (validateInput()) {
                startTimer();
                startButton.setDisable(true);
                stopButton.setDisable(false);
                readyButton.setDisable(true);
                cancelButton.setDisable(true);
            }
        });

        stopButton.setOnAction(event -> {
            if (allowStop) {
                stopTimer();
                startButton.setDisable(false);
                stopButton.setDisable(true);
                readyButton.setDisable(false);
                cancelButton.setDisable(true);
            }
        });
        stopButton.setDisable(true);

        deleteButton.setOnAction(event -> deleteSelectedRecord());
        exportButton.setOnAction(event -> exportToCSV());

        tableView = new TableView<>();
        tableView.getColumns().addAll(
                createColumn("Order", 0, 50),
                createColumn("Player Name", 1, 150),
                createColumn("Team", 2, 150),
                createColumn("Time", 3, 100)
        );

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            deleteButton.setDisable(newSelection == null);
        });

        HBox nameTeamBox = new HBox(10, new Label("Player name:"), nameField, new Label("Team:"), teamField);
        HBox buttonsBox = new HBox(10, startButton, readyButton, cancelButton, stopButton);
        HBox disableStopBox = new HBox(10, new Label("Disable Stop (seconds):"), disableStopSpinner);

        HBox statusBox = new HBox(statusLabel, connectionBox, soundBox, sensorBox);
        statusBox.setSpacing(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(connectionBox, Priority.ALWAYS);

        Label resultsLabel = new Label("RESULTS");
        resultsLabel.setStyle("-fx-font-size: 1.5em;");
        HBox resultsBox = new HBox(resultsLabel);
        resultsBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(
                statusBox,
                connectionBox,
                soundBox,
                sensorBox,
                timerLabel,
                disableStopBox,
                nameTeamBox,
                buttonsBox,
                new Separator(),
                resultsBox,
                new HBox(10, deleteButton, exportButton),
                tableView
        );

        Scene scene = new Scene(root, 600, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Arduino Control");
        primaryStage.show();

        deleteButton.setDisable(true);
        exportButton.setDisable(true);

        tableView.getItems().addListener((ListChangeListener<ObservableList<String>>) change -> {
            exportButton.setDisable(tableView.getItems().isEmpty());
        });

        disableStopSpinner.valueProperty().addListener((obs, oldValue, newValue) -> disableStopSeconds = newValue);

        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DELETE && !deleteButton.isDisabled()) {
                deleteSelectedRecord();
                event.consume();
            } else if (event.getCode() == KeyCode.E && event.isControlDown() && !exportButton.isDisabled()) {
                exportToCSV();
                event.consume();
            }
        });

        new Thread(() -> {
            SerialPort[] ports = SerialPort.getCommPorts();
            for (SerialPort port : ports) {
                if (port.getDescriptivePortName().contains("COM3")) {
                    chosenPort = port;
                    break;
                }
            }
            if (chosenPort == null) {
                System.out.println("Arduino port not found");
                return;
            }

            chosenPort.openPort();
            chosenPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

            Platform.runLater(() -> connectionIndicator.setFill(Color.GREEN));

            while (true) {
                if (chosenPort.bytesAvailable() > 0) {
                    byte[] readBuffer = new byte[1];
                    chosenPort.readBytes(readBuffer, 1);

                    if (readBuffer[0] == '0' && allowStop) {
                        Platform.runLater(() -> stopButton.fire());
                    }

                    if (readBuffer[0] == '1' && sensorStartEnabled && readyForSensorStart) {
                        Platform.runLater(() -> startButton.fire());
                        readyForSensorStart = false;
                    }
                }
            }
        }).start();
    }

    private boolean validateInput() {
        if (nameField.getText().isEmpty()) {
            showAlert("Please enter player name.");
            return false;
        }
        if (teamField.getText().isEmpty()) {
            showAlert("Please enter team.");
            return false;
        }
        return true;
    }

    private void startTimer() {
        currentName = nameField.getText();
        currentTeam = teamField.getText();
        elapsedSeconds = 0;
        elapsedMillis = 0;

        timeline = new Timeline(new KeyFrame(Duration.millis(10), event -> {
            elapsedMillis += 10;
            if (elapsedMillis >= 1000) {
                elapsedMillis = 0;
                elapsedSeconds++;
            }
            updateTimerLabel();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        statusLabel.setText("Timer is running");
        statusLabel.setTextFill(Color.GREEN);
        isRunning = true;

        allowStop = false;
        if (disableStopSeconds > 0) {
            new Timeline(new KeyFrame(Duration.seconds(disableStopSeconds), event -> allowStop = true)).play();
        } else {
            allowStop = true;
        }
    }

    private void stopTimer() {
        if (timeline != null) {
            timeline.stop();
        }

        String elapsedTime = String.format("%02d:%02d:%02d.%02d", elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60, elapsedMillis / 10);
        ObservableList<String> newRow = FXCollections.observableArrayList(
                String.valueOf(tableView.getItems().size() + 1),
                currentName,
                currentTeam,
                elapsedTime
        );

        tableView.getItems().add(newRow);
        tableView.getItems().sort(Comparator.comparing(o -> o.get(3)));

        for (int i = 0; i < tableView.getItems().size(); i++) {
            tableView.getItems().get(i).set(0, String.valueOf(i + 1));
        }

        statusLabel.setText("Timer is not running");
        statusLabel.setTextFill(Color.RED);
        isRunning = false;

        if (soundEnabled) {
            mediaPlayer.stop();
            mediaPlayer.play();
        }
    }

    private void updateTimerLabel() {
        String elapsedTime = String.format("%02d:%02d:%02d.%02d", elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60, elapsedMillis / 10);
        timerLabel.setText("Elapsed time: " + elapsedTime);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private TableColumn<ObservableList<String>, String> createColumn(String title, int index, int width) {
        TableColumn<ObservableList<String>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(index)));
        column.setPrefWidth(width);
        return column;
    }

    private void deleteSelectedRecord() {
        ObservableList<String> selectedRecord = tableView.getSelectionModel().getSelectedItem();
        if (selectedRecord != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText(null);
            alert.setContentText("Are you sure you want to delete this record?");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    tableView.getItems().remove(selectedRecord);
                    for (int i = 0; i < tableView.getItems().size(); i++) {
                        tableView.getItems().get(i).set(0, String.valueOf(i + 1));
                    }
                }
            });
        }
    }

    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save as CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                for (ObservableList<String> row : tableView.getItems()) {
                    writer.write(String.join(";", row) + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}