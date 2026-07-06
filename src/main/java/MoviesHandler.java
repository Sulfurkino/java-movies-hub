import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {

        switch (ex.getRequestMethod()) {

            case "GET":
                handleGet(ex);
                break;

            case "POST":
                handlePost(ex);
                break;

            case "DELETE":
                handleDelete(ex);
                break;

            default:
                ex.sendResponseHeaders(405, -1);
                ex.close();
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        if (query != null) {
            getMoviesByYear(ex, query);
            return;
        }

        if (path.equals("/movies")) {
            getAllMovies(ex);
            return;
        }

        getMovieById(ex, path);

    }

    private void getAllMovies(HttpExchange ex) throws IOException {

        String json = gson.toJson(store.getAllMovies());

        sendJson(ex, 200, json);
    }

    private void getMovieById(HttpExchange ex, String path)
            throws IOException {

        String idString = path.substring("/movies/".length());

        int id;

        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            sendJson(
                    ex,
                    400,
                    gson.toJson(new ErrorResponse("Некорректный ID"))
            );
            return;
        }

        Optional<Movie> movie = store.getMovieById(id);

        if (movie.isPresent()) {
            sendJson(
                    ex,
                    200,
                    gson.toJson(movie.get())
            );
            return;
        }

        sendJson(
                ex,
                404,
                gson.toJson(new ErrorResponse("Фильм не найден"))
        );
    }

    private void getMoviesByYear(HttpExchange ex, String query)
            throws IOException {

        if (!query.startsWith("year=")) {
            sendJson(
                    ex,
                    400,
                    gson.toJson(new ErrorResponse("Некорректный параметр запроса 'year'"))
            );
            return;
        }

        String yearString = query.substring("year=".length());

        int year;

        try {
            year = Integer.parseInt(yearString);
        } catch (NumberFormatException e) {
            sendJson(
                    ex,
                    400,
                    gson.toJson(new ErrorResponse("Некорректный параметр запроса 'year'"))
            );
            return;
        }

        String json = gson.toJson(store.getMoviesByYear(year));

        sendJson(ex, 200, json);
    }

    private void handleDelete(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();

        String idString = path.substring("/movies/".length());

        int id;

        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            sendJson(
                    ex,
                    400,
                    gson.toJson(new ErrorResponse("Некорректный ID"))
            );
            return;
        }

        if (store.delete(id)) {
            sendNoContent(ex);
            return;
        }

        sendJson(
                ex,
                404,
                gson.toJson(new ErrorResponse("Фильм не найден"))
        );
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");


        if (contentType == null || !contentType.startsWith("application/json")) {
            ex.sendResponseHeaders(415, -1);
            ex.close();
            return;
        }
        String body = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 422, gson.toJson(
                    new ErrorResponse("Некорректный JSON")
            ));
            return;
        }

        List<String> errors = new ArrayList<>();

        if (movie == null) {
            sendJson(ex, 422,
                    gson.toJson(new ErrorResponse("Некорректный JSON")));
            return;
        }
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            errors.add("Название не должно быть пустым");
        }
        if (movie.getTitle() != null && movie.getTitle().length() > 100) {
            errors.add("Название не должно быть длиннее 100 символов");
        }

        int maxYear = Year.now().getValue() + 1;

        if (movie.getYear() < 1888 || movie.getYear() > maxYear) {
            errors.add("Год должен быть между 1888 и " + maxYear);
        }
        if (!errors.isEmpty()) {
            sendJson(
                    ex,
                    422,
                    gson.toJson(new ErrorResponse("Ошибка валидации", errors))
            );
            return;
        }
        store.add(movie);

        String json = gson.toJson(movie);

        sendJson(ex, 201, json);
    }

}