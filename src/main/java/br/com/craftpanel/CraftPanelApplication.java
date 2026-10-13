package br.com.craftpanel;

import br.com.craftpanel.config.ConfigLoader;
import br.com.craftpanel.config.PanelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftPanelApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftPanelApplication.class);

    public static void main(String[] args) {
        PanelConfig config = ConfigLoader.load("craftpanel.yml");
        CraftPanelServer server = CraftPanelServer.getInstance();
        server.start(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown hook triggered");
            server.stop();
        }));
    }
}
