package org.ts.clipharbor.UI;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.*;
        import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.io.File;

public class ClipHarborUI extends Application {

    private TextField urlField;
    private TextField folderField;
    private Button browseButton;
    private Button downloadButton;
    private ProgressBar progressBar;
    private TextArea statusArea;
    private File selectedDirectory;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Video Downloader");

        Label urlLabel = new Label("Page URL:");
        urlField = new TextField();
        urlField.setPromptText("Enter URL here");
        HBox urlBox = new HBox(10, urlLabel, urlField);
        urlBox.setAlignment(Pos.CENTER_LEFT);

        Label folderLabel = new Label("Save Folder:");
        folderField = new TextField();
        folderField.setPrefWidth(300);
        folderField.setEditable(false);
        browseButton = new Button("Browse");
        browseButton.setOnAction(e -> chooseFolder(primaryStage));
        HBox folderBox = new HBox(10, folderLabel, folderField, browseButton);
        folderBox.setAlignment(Pos.CENTER_LEFT);

        downloadButton = new Button("Download");
        downloadButton.setOnAction(e -> startDownload());

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setMinHeight(20);

        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setWrapText(true);
        statusArea.setPrefHeight(200);

        VBox root = new VBox(15, urlBox, folderBox, downloadButton, progressBar, statusArea);
        root.setPadding(new Insets(20));
        root.setPrefWidth(600);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Save Folder");
        if(selectedDirectory != null) {
            directoryChooser.setInitialDirectory(selectedDirectory);
        }
        File folder = directoryChooser.showDialog(stage);
        if(folder != null) {
            selectedDirectory = folder;
            folderField.setText(folder.getAbsolutePath());
        }
    }

    private void startDownload() {
        String url = urlField.getText();
        String folder = folderField.getText();

        if (url.isEmpty() || folder.isEmpty()) {
            appendStatus("Please enter a URL and select a save folder.");
            return;
        }

        appendStatus("Starting download for URL: " + url);
        // Download logic will go here later.

        // For demo: reset progress bar
        progressBar.setProgress(0);
    }

    private void appendStatus(String message) {
        statusArea.appendText(message + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}