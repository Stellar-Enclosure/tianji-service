package top.egon.cola.platform.tianji.process.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TianjiProcessServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TianjiProcessServerApplication.class, args);
    }
}
