package com.coco.subnet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subnetCalculatorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Subnet Calculator API")
                .version("1.0")
                .description("Calcul CIDR, découpage VLSM et plan d'adressage par VLAN (IPv4). "
                        + "Les erreurs suivent le format RFC 9457 (application/problem+json) avec un champ « code »."));
    }
}
