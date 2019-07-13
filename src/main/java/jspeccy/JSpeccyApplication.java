package jspeccy;

import gui.JSpeccy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.awt.*;

@SpringBootApplication
@EnableConfigurationProperties
public class JSpeccyApplication implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(JSpeccyApplication.class);

    public static void main(String[] args) {

        if (System.getProperty("com.sun.management.jmxremote") == null) {
            LOGGER.info("JMX remoting is disabled");
        } else {
            String portString = System.getProperty("com.sun.management.jmxremote.port");
            if (portString != null) {
                LOGGER.info("JMX server is running on port {}", Integer.parseInt(portString));
            } else {
                LOGGER.warn("JMX remote port is not configured");
            }
        }

        new SpringApplicationBuilder(JSpeccyApplication.class).headless(false).run(args);
        final JSpeccy jSpeccy = new JSpeccy(args);

        EventQueue.invokeLater(() -> {

            // Ideally we would rely on Spring to create the JSpeccy bean and then retrieve from the context. e.g
            //
            // ConfigurableApplicationContext ctx =
            //      new SpringApplicationBuilder(JSpeccyApplication.class).headless(false).run(args);
            // ctx.getBean(JSpeccy.class).setVisible(true);
            //
            // But for simplicity we will invoke the JSpeccy class directly.
            jSpeccy.setVisible(true);
        });
    }

    @Override
    public void run(String... arg0) {

        if (arg0.length > 0 && arg0[0].equals("exitcode")) {
            throw new ExitException();
        }
    }

    /* package */ static class ExitException extends RuntimeException implements ExitCodeGenerator {

        private static final long serialVersionUID = 1L;

        @Override
        public int getExitCode() {

            return 10;
        }

    }

}
