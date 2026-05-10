package notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import shared.RabbitNames;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaRepositories
@org.springframework.amqp.rabbit.annotation.EnableRabbit
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

    @Configuration
    static class AmqpBeans {

        @Bean
        TopicExchange tripEventsExchange() {
            return new TopicExchange(RabbitNames.TRIP_EVENTS_EXCHANGE, true, false);
        }

        @Bean
        Queue tripNotifyQueue() {
            return new Queue(RabbitNames.TRIP_NOTIFY_QUEUE, true);
        }

        @Bean
        Binding tripNotifyBinding(Queue tripNotifyQueue, TopicExchange tripEventsExchange) {
            return BindingBuilder.bind(tripNotifyQueue)
                    .to(tripEventsExchange)
                    .with(RabbitNames.TRIP_NOTIFY_ROUTING_KEY);
        }

        @Bean
        MessageConverter rabbitMessageConverter() {
            return new Jackson2JsonMessageConverter();
        }
    }

    @Configuration
    static class JwtBeans {
        @Bean
        JwtDecoder jwtDecoder(@Value("${taxi.jwt.secret}") String secret) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key).build();
        }
    }

    @Configuration
    @EnableWebSecurity
    static class WebSecurity {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
            return http.build();
        }
    }
}
