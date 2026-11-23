package gatesofhumanity.models;

public record Location(int id, String city, double latitude, double longitude, String timezone) {
    public static final Location LONDON = new Location(
        2643743,
        "London, England, United Kingdom",
        51.50853, -0.12574,
        "Europe/London");
}
