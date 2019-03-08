package com.threathunter.nebula.onlineserver;

import com.threathunter.common.ShutdownHookManager;
import com.threathunter.config.CommonDynamicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;


/**
 * @author wenlu
 */
public class ServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {

        // first public common config: nebula.conf
        CommonDynamicConfig.getInstance().addConfigFile("nebula.conf");
        // next private config online.conf
        CommonDynamicConfig.getInstance().addConfigFile("online.conf");

        printStartupAndShutdownMsg(args);
        try {
            final OnlineServer server = new OnlineServer();
            ShutdownHookManager.get().addShutdownHook(() -> {
                server.stop();
            },Integer.MAX_VALUE);
            server.start();
        } catch (Exception e) {
            LOGGER.error("init:fatal:Can not to start the server, it is going to shutdown", e);
            System.exit(-1);
        }
    }

    private static void printStartupAndShutdownMsg(String[] args) {
        String host= "Unknown";
        try {
            host = InetAddress.getLocalHost().toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        final String hostName = host;
        final String className = ServerMain.class.getSimpleName();

        LOGGER.warn("STARTUP_MSG:\n" +
                        "*******************************************\n" +
                        "\tStarting : {}\n" +
                        "\tHost : {}\n" +
                        "\tArgs : {}\n" +
                        "*******************************************",
                className, hostName, Arrays.toString(args));

        ShutdownHookManager.get().addShutdownHook(new Runnable() {
            @Override
            public void run() {
                LOGGER.warn("SHUTDOWN_MSG:\n" +
                                "*******************************************\n" +
                                "\tShutting down : {}\n" +
                                "\tHost : {}\n" +
                                "*******************************************",
                        className, hostName);

            }
        }, 1);
    }
}
