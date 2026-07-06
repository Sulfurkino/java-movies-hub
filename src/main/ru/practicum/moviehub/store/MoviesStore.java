package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values()
                .stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }

    public Movie add(Movie movie) {
        movie.setId(nextId);
        nextId++;
        movies.put(movie.getId(), movie);
        return movie;
    }

    public boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }
}

