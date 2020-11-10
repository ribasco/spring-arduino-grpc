package com.ibasco.test.grpc.arduinogrpc;

import cc.arduino.cli.commands.ArduinoCoreGrpc;
import cc.arduino.cli.commands.Board;
import cc.arduino.cli.commands.Commands;
import cc.arduino.cli.commands.Common;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;
import java.util.List;

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
    
    private Parent initRootNode() {
        var root = new AnchorPane();
        root.setPrefWidth(1024);
        root.setPrefHeight(768);

        var lvBoards = new ListView<>();
        var btnLoadBoards = new Button("List Supported Boards");

        btnLoadBoards.setOnAction(event -> {
            log.debug("Running gRPC");
            if (!initGrpc()) {
                log.error("Failed to connect to Arduino gRPC service");
                throw new IllegalStateException("Failed to initialize arduino-cli gRPC service");
            }
            var items = getBoardListAll();
            lvBoards.setItems(FXCollections.observableArrayList(items));
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

    private boolean initGrpc() {
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
        }
        return instance != null;
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

    private void checkInstance() {
        if (this.instance == null)
            throw new IllegalStateException("Not initialized");
    }

    public static void main(String[] args) {
        launch(args);
    }

}
