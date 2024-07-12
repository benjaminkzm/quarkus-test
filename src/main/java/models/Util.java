package models;

import java.time.Duration;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        String path = "/";
        int maxAge = (int) Duration.ofDays(180).getSeconds();
        boolean httpOnly = true;
        boolean secure = false;
        String sameSite = "Strict";

        String cookieValue = value + "; Max-Age=" + maxAge + "; Path=" + path + "; SameSite=" + sameSite;
        if (secure) {
            cookieValue += "; Secure";
        }
        if (httpOnly) {
            cookieValue += "; HttpOnly";
        }

        return new NewCookie(name, cookieValue, path, null, null, maxAge, secure);
    }
}
