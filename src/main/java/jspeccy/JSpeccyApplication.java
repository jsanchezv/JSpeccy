package jspeccy;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties
@Configuration
@Import(JSpeccyApplicationConfiguration.class)
public class JSpeccyApplication {

    public static void main(String[] args) {

        new SpringApplicationBuilder(JSpeccyApplication.class).headless(false).run(args);
    }

}
