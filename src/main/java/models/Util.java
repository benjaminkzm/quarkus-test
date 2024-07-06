package models;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Util {

    public static String prefix(HttpServletRequest request, UriInfo uriInfo) {
        boolean secure = request.isSecure() || getForwardedProtoHeader(request).contains("https");

        String prefix;
        if (request.getServerName().equals("localhost")) {
            prefix = "../";
            long countSlash = uriInfo.getPath().chars().filter(ch -> ch == '/').count() - 1;
            for (long i = 0; i < countSlash; ++i) {
                prefix += "../";
            }
            prefix = prefix.substring(0, prefix.length() - 1);
        } else {
            prefix = (secure ? "https://" : "http://") + request.getServerName();
        }
        return prefix;
    }

    private static List<String> getForwardedProtoHeader(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeaders("X-Forwarded-Proto"))
                .map(Collections::list)
                .orElse(Collections.emptyList());
    }

    public static ObjectNode toJson(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_DEFAULT);
        return (ObjectNode) mapper.convertValue(obj, JsonNode.class);
    }

    public static NewCookie buildCookie(String name, String value) {
        return new NewCookie(name, value, "/", null, null, (int) Duration.ofDays(180).getSeconds(), true, true);
    }
}
