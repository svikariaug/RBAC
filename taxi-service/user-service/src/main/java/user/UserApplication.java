package user;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaRepositories
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }

    @Bean
    ApplicationRunner cacheWarmup(UserService userService) {
        return args -> userService.warmCache();
    }

    @Configuration
    static class JwtBeans {

        @Bean
        JwtEncoder jwtEncoder(@Value("${taxi.jwt.secret}") String secret) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            return new NimbusJwtEncoder(new ImmutableSecret<>(bytes));
        }

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
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @RestControllerAdvice
    static class UserErrors {

        @ExceptionHandler(NotFoundException.class)
        public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
