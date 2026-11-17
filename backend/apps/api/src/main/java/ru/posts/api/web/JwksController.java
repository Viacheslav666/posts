package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.security.jwt.JwksService;

@RestController
@Tag(name = "JWKS", description = "Public signing keys for access JWT verification")
@SecurityRequirements
public class JwksController {

    private final JwksService jwksService;

    public JwksController(JwksService jwksService) {
        this.jwksService = jwksService;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "jwksGet", summary = "JSON Web Key Set (ES256)")
    public Map<String, Object> jwks() {
        return jwksService.jwks();
    }
}
