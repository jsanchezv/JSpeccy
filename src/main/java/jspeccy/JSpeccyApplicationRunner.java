package jspeccy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@Slf4j
public class JSpeccyApplicationRunner implements CommandLineRunner, ExitCodeGenerator {

    private final JSpeccyCommand command;
    private final CommandLine.IFactory factory;
    private int exitCode;

    @Autowired
    public JSpeccyApplicationRunner(final JSpeccyCommand command, final CommandLine.IFactory factory) {

        this.command = command;
        this.factory = factory;
    }

    @Override
    public void run(String... args) {

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

        exitCode = new CommandLine(command, factory)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
    }

    @Override
    public int getExitCode() {

        return exitCode;
    }

}
