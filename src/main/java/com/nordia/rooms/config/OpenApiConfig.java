package com.nordia.rooms.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String PROBLEM_DETAIL = "ProblemDetail";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI nordiaOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .info(new Info()
                        .title("Nordia Meeting Rooms API")
                        .description("Book and cancel meeting rooms at the Nordia office.")
                        .version("1.0"));
    }

    // Every error response is a problem detail, so the endpoints only describe when
    // an error happens and this adds the body to each 4xx and 5xx response.
    @Bean
    OpenApiCustomizer problemDetailResponses() {
        return openApi -> {
            Schema<?> fieldError = new ObjectSchema()
                    .addProperty("field", new StringSchema())
                    .addProperty("message", new StringSchema());

            Schema<?> problemDetail = new ObjectSchema()
                    .addProperty("type", new StringSchema().example("about:blank"))
                    .addProperty("title", new StringSchema().example("Conflict"))
                    .addProperty("status", new IntegerSchema().example(409))
                    .addProperty("detail", new StringSchema()
                            .example("Room 1 is already booked from 09:00 to 10:00 on 2026-11-17."))
                    .addProperty("instance", new StringSchema().example("/api/bookings"))
                    .addProperty("code", new StringSchema().example("BOOKING_OVERLAP")
                            .description("Stable error code, safe to branch on."))
                    .addProperty("errors", new ArraySchema().items(fieldError)
                            .description("Only for VALIDATION_ERROR: one entry per invalid field."));

            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetail);

            Content problemContent = new Content().addMediaType(PROBLEM_JSON,
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL)));

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((code, response) -> {
                        if (code.startsWith("4") || code.startsWith("5")) {
                            response.setContent(problemContent);
                        }
                    })));
        };
    }
}
