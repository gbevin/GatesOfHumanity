package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.UrlBuilder;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The kinds of wheel, by the type in their URLs.
 */
public enum ChartType {
    TRANSIT("", "wheel", PageDescription.TRANSIT),
    BIRTH("birth", "wheel_birth", PageDescription.BIRTH),
    BIRTH_TRANSIT("birth-transit", "wheel_birth_transit", PageDescription.BIRTH_TRANSIT),
    CONNECTION("connection", "wheel_connection", PageDescription.CONNECTION),
    CONNECTION_TRANSIT("connection-transit", "wheel_connection_transit", PageDescription.CONNECTION_TRANSIT);

    public static final String TYPED_PATTERN = Arrays.stream(values()).filter(type -> type != TRANSIT)
        .map(type -> Pattern.quote(type.parameter_)).collect(Collectors.joining("|"));

    public final String parameter_;
    final String template_;
    final PageDescription page_;

    ChartType(String parameter, String template, PageDescription page) {
        parameter_ = parameter;
        template_ = template;
        page_ = page;
    }

    public UrlBuilder url(Context c, AppSite site) {
        return this == TRANSIT ? c.urlFor(site.wheel) : c.urlFor(site.wheelChart).param("type", parameter_);
    }

    UrlBuilder imageUrl(Context c, AppSite site) {
        return this == TRANSIT ? c.urlFor(site.wheelImage) : c.urlFor(site.wheelImage).param("type", parameter_);
    }

    /**
     * The chart type of a URL, the transits when it has none.
     */
    static ChartType of(String parameter) {
        for (var type : values()) {
            if (type.parameter_.equals(parameter)) {
                return type;
            }
        }
        return TRANSIT;
    }
}
