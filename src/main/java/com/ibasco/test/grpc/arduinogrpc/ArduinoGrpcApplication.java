package com.ibasco.test.grpc.arduinogrpc;

import cc.arduino.cli.commands.ArduinoCoreGrpc;
import cc.arduino.cli.commands.Board;
import cc.arduino.cli.commands.Commands;
import cc.arduino.cli.commands.Common;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SpringBootApplication
public class ArduinoGrpcApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(ArduinoGrpcApplication.class);

    private final ArduinoCoreGrpc.ArduinoCoreBlockingStub blockingStub;

    private final ArduinoCoreGrpc.ArduinoCoreFutureStub futureStub;

    private Common.Instance instance;

    public ArduinoGrpcApplication() {
        var channelBuilder = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext();
        var channel = channelBuilder.build();
        this.blockingStub = ArduinoCoreGrpc.newBlockingStub(channel);
        this.futureStub = ArduinoCoreGrpc.newFutureStub(channel);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        var context = SpringApplication.run(ArduinoGrpcApplication.class);
        primaryStage.setScene(initScene());
        primaryStage.show();

    }

    private Scene initScene() {
        return new Scene(initRootNode());
    }

    private boolean rpcInitialized = false;

    private Parent initRootNode() {
        var root = new AnchorPane();
        root.setPrefWidth(1024);
        root.setPrefHeight(768);

        var lvBoards = new ListView<>();
        var btnLoadBoards = new Button("List Supported Boards");

        btnLoadBoards.setOnAction(event -> {
            try {
                log.debug("Running gRPC");
                if (!initGrpc()) {
                    log.error("Failed to connect to Arduino gRPC service");
                    throw new IllegalStateException("Failed to initialize arduino-cli gRPC service");
                } else {
                    log.debug("Already initialized");
                }
                var items = getBoardListAll();
                lvBoards.setItems(FXCollections.observableArrayList(items));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        AnchorPane.setLeftAnchor(lvBoards, 10.0d);
        AnchorPane.setTopAnchor(lvBoards, 10.0d);
        AnchorPane.setRightAnchor(lvBoards, 10.0d);
        AnchorPane.setBottomAnchor(lvBoards, 50.0d);
        AnchorPane.setBottomAnchor(btnLoadBoards, 10.0d);
        AnchorPane.setLeftAnchor(btnLoadBoards, 10.0d);
        AnchorPane.setRightAnchor(btnLoadBoards, 10.0d);
        root.getChildren().addAll(btnLoadBoards, lvBoards);

        return root;
    }

    private boolean initGrpc() throws Exception {
        if (rpcInitialized)
            return true;
        log.debug("Starting arduino-cli daemon");
        if (process == null)
            initArduinoCliProcess();
        rpcInitialized = false;
        var initReq = Commands.InitReq.newBuilder().build();
        try {
            var res = blockingStub.init(initReq);
            if (res.hasNext()) {
                var initRes = res.next();
                this.instance = initRes.getInstance();
            } else {
                this.instance = null;
            }
        } catch (StatusRuntimeException e) {
            log.error("Error init request", e);
            this.instance = null;
        } finally {
            rpcInitialized = this.instance != null;
        }
        return rpcInitialized;
    }

    private List<Board.BoardListItem> getBoardListAll() {
        checkInstance();
        var request = Board.BoardListAllReq.newBuilder().setInstance(this.instance).build();
        try {
            var response = blockingStub.boardListAll(request);
            return response.getBoardsList();
        } catch (Exception e) {
            log.error("Failed to obtain board list", e);
            return Collections.emptyList();
        }
    }

    private static class StreamGobbler implements Runnable {

        private InputStream inputStream;

        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }

    private boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

    private Process process;

    private void initArduinoCliProcess() throws Exception {
        var builder = new ProcessBuilder();
        if (isWindows) {
            builder.command("cmd.exe", "/c", "arduino-cli", "-v", "daemon");
        } else {
            builder.command("sh", "-c", "arduino-cli", "-v", "daemon");
        }
        builder.directory(new File(System.getProperty("user.home")));
        process = builder.start();
        StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), log::info);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        //assert exitCode == 0;
    }

    private void checkInstance() {
        if (this.instance == null)
            throw new IllegalStateException("Not initialized");
    }

    public static void main(String[] args) {
        launch(args);
    }

}
