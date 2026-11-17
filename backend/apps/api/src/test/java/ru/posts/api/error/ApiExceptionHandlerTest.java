package ru.posts.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void apiExceptionMapsCodeProperty() {
        ResponseEntity<ProblemDetail> response = handler.handleApi(new NotImplementedException("workspacesList"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", ProblemCodes.NOT_IMPLEMENTED);
        assertThat(response.getBody().getDetail()).contains("workspacesList");
    }
}
