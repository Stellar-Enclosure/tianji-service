package top.egon.cola.platform.tianji.agent.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class TianjiAgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TianjiAgentServerApplication.class, args);
    }
}
