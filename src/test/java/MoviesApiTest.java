import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer();
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void beforeEach() {
        server.getStore().clear();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_whenMovieExists_returnsMovieList() throws Exception {

        server.getStore().add(new Movie("Interstellar", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        assertEquals(
                "application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse("")
        );

        String body = resp.body();

        assertTrue(body.contains("Interstellar"));
        assertTrue(body.contains("2014"));
    }

    @Test
    void postMovie_whenValid_returnsCreatedMovie() throws Exception {

        String json = """
                {
                  "title":"Interstellar",
                  "year":2014
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode());

        String body = resp.body();

        assertTrue(body.contains("Interstellar"));
        assertTrue(body.contains("2014"));
        assertTrue(body.contains("\"id\""));
    }

    @Test
    void postMovie_whenTitleIsEmpty_returnsValidationError() throws Exception {

        String json = """
                {
                  "title":"",
                  "year":2014
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();

        assertTrue(body.contains("Ошибка валидации"));
        assertTrue(body.contains("Название не должно быть пустым"));
    }

    @Test
    void postMovie_whenTitleTooLong_returnsValidationError() throws Exception {

        String title = "A".repeat(101);

        String json = """
                {
                  "title":"%s",
                  "year":2014
                }
                """.formatted(title);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();

        assertTrue(body.contains("Ошибка валидации"));
        assertTrue(body.contains("Название не должно быть длиннее 100 символов"));
    }

    @Test
    void postMovie_whenYearInvalid_returnsValidationError() throws Exception {

        String json = """
                {
                  "title":"Interstellar",
                  "year":1800
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();

        assertTrue(body.contains("Ошибка валидации"));
        assertTrue(body.contains("Год должен быть между"));
    }

    @Test
    void postMovie_whenContentTypeInvalid_returns415() throws Exception {

        String json = """
                {
                  "title":"Interstellar",
                  "year":2014
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode());
    }

    @Test
    void postMovie_whenJsonInvalid_returns422() throws Exception {

        String json = """
                {
                  "title":
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный JSON"));
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {

        String json = """
                {
                  "title":"Interstellar",
                  "year":2014
                }
                """;

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body();

        assertTrue(body.contains("Interstellar"));
        assertTrue(body.contains("2014"));
        assertTrue(body.contains("\"id\":1"));
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/100"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {

        String json = """
                {
                  "title":"Interstellar",
                  "year":2014
                }
                """;

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(deleteRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/100"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovie_whenIdIsNotNumber_returns400() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void getMoviesByYear_whenYearExists_returnsMovies() throws Exception {

        server.getStore().add(new Movie("Interstellar", 2014));
        server.getStore().add(new Movie("Matrix", 1999));
        server.getStore().add(new Movie("Inception", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2014"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body();

        assertTrue(body.contains("Interstellar"));
        assertTrue(body.contains("Inception"));

        assertFalse(body.contains("Matrix"));
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный параметр запроса"));
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode());
    }

    @Test
    void getMoviesByYear_whenNoMovies_returnsEmptyArray() throws Exception {

        server.getStore().add(new Movie("Interstellar", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }
}