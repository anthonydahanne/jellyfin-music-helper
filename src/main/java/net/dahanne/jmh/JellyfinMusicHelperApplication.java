package net.dahanne.jmh;

import net.dahanne.jmh.config.JellyfinProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JellyfinProperties.class)
public class JellyfinMusicHelperApplication {

	static void main(String[] args) {
		SpringApplication.run(JellyfinMusicHelperApplication.class, args);
	}

}
