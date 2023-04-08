package jspeccy;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class JSpeccyApplication {

    public static void main(String[] args) {

        new SpringApplicationBuilder(JSpeccyApplication.class).headless(false).run(args);
    }

}
