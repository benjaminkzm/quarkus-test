package models;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.UriInfo;

public class Util {

    public static String prefix(UriInfo uriInfo, HttpHeaders httpHeaders) {
        boolean secure = httpHeaders.getRequestHeader("X-Forwarded-Proto").contains("https");
        String host = uriInfo.getBaseUri().getHost();
        String path = uriInfo.getPath();
        
        String prefix;
        if (host.equals("localhost")) {
            prefix = "../";
            long countSlash = path.chars().filter(ch -> ch == '/').count() - 1;
            for (long i = 0; i < countSlash; ++i) {
                prefix += "../";
            }
            prefix = prefix.substring(0, prefix.length() - 1);
        } else {
            prefix = (secure ? "https://" : "http://") + host;
        }
        return prefix;
    }

    public static ObjectNode toJson(Object obj) { 
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_DEFAULT);
        return (ObjectNode) mapper.convertValue(obj, JsonNode.class);
    }

    public static NewCookie buildCookie(String name, String value) {
        int maxAge = (int) Duration.ofDays(180).getSeconds();
        
        return new NewCookie.Builder(name)
                .value(value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(false)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }
}
