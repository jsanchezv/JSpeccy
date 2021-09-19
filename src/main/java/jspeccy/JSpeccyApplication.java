package jspeccy;

import gui.JSpeccy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
@Slf4j
public class JSpeccyApplication implements CommandLineRunner {

    public static void main(String[] args) {

        new SpringApplicationBuilder(JSpeccyApplication.class)
                .headless(false)
                .run(args);
    }

    @Override
    public void run(String... arg0) {

        if (System.getProperty("com.sun.management.jmxremote") == null) {
            log.info("JMX remoting is disabled");
        } else {
            String portString = System.getProperty("com.sun.management.jmxremote.port");
            if (portString != null) {
                log.info("JMX server is running on port {}", Integer.parseInt(portString));
            } else {
                log.warn("JMX remote port is not configured");
            }
        }

        new JSpeccy(arg0).setVisible(true);
    }

}
