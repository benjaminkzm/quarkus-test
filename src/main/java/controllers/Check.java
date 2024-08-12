package controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import services.CheckService;

@ApplicationScoped
@jakarta.ws.rs.Path("/check")
public class Check {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private ObjectMapper objectMapper;

    @Inject
    private CheckService checkService;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @POST
    @jakarta.ws.rs.Path("/html")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public CompletableFuture<Response> checkHTML(MultivaluedMap<String, String> params) throws IOException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<Path, String> submissionFiles = new TreeMap<>();
                String repo = "";
                String problem = "";
                String ccid = null;

                for (String key : params.keySet()) {
                    String value = params.getFirst(key);
                    if ("repo".equals(key))
                        repo = value;
                    else if ("problem".equals(key))
                        problem = value;
                    else if (!"ccid".equals(key))
                        ccid = value;
                    else
                        submissionFiles.put(Paths.get(key), value);
                }

                if (ccid == null) {
                    Optional<String> ccidCookie = Optional.ofNullable(uriInfo.getQueryParameters().getFirst("ccid"));
                    ccid = ccidCookie.orElse(Util.createPronouncableUID());
                }

                String result = checkService.checkHTML(repo, problem, ccid, submissionFiles);
                return Response.ok(result).type(MediaType.TEXT_HTML).header("Set-Cookie", "ccid=" + ccid).build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
        }, executorService);
    }

    @POST
    @jakarta.ws.rs.Path("/run")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_JSON })
    public CompletableFuture<Response> run() throws IOException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<Path, String> submissionFiles = new TreeMap<>();
                String contentType = headers.getHeaderString(HttpHeaders.CONTENT_TYPE);

                if (MediaType.APPLICATION_FORM_URLENCODED.equals(contentType)) {
                    MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
                    for (String key : params.keySet()) {
                        String value = params.getFirst(key);
                        submissionFiles.put(Paths.get(key), value);
                    }
                    return Response.ok(checkService.run(submissionFiles)).type(MediaType.TEXT_PLAIN).build();
                } else if (MediaType.MULTIPART_FORM_DATA.equals(contentType)) {
                    // Handle multipart form data
                    // Note: Handling multipart form data in Jakarta EE might require a different approach,
                    // such as using @MultipartForm or handling file uploads explicitly.
                    return Response.ok().build(); // Placeholder
                } else if (MediaType.APPLICATION_JSON.equals(contentType)) {
                    String jsonString = uriInfo.getQueryParameters().getFirst("jsonBody"); // Get JSON string from query parameter
                    JsonNode json = objectMapper.readTree(jsonString); // Parse JSON string to JsonNode
                    return Response.ok(checkService.runJSON(json)).type(MediaType.APPLICATION_JSON).build();
                } else {
                    return Response.serverError().entity("Bad content type").build();
                }
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
        }, executorService);
    }

    @POST
    @jakarta.ws.rs.Path("/njs")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON })
    public CompletableFuture<Response> checkNJS() throws IOException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String ccid = null;
                String repo = "ext";
                String problem = null;
                Map<Path, String> submissionFiles = new TreeMap<>();

                if (MediaType.APPLICATION_FORM_URLENCODED.equals(headers.getHeaderString(HttpHeaders.CONTENT_TYPE))) {
                    MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
                    for (String key : params.keySet()) {
                        String value = params.getFirst(key);
                        if ("repo".equals(key)) repo = value;
                        else if ("problem".equals(key)) problem = value;
                        else if ("ccid".equals(key)) ccid = value;
                        else submissionFiles.put(Paths.get(key), value);
                    }
                } else if (MediaType.APPLICATION_JSON.equals(headers.getHeaderString(HttpHeaders.CONTENT_TYPE))) {
                    String jsonString = uriInfo.getQueryParameters().getFirst("jsonBody"); // Get JSON string from query parameter
                    JsonNode json = objectMapper.readTree(jsonString); // Parse JSON string to JsonNode
                    Iterator<Map.Entry<String, JsonNode>> iter = json.fields();
                    while (iter.hasNext()) {
                        Map.Entry<String, JsonNode> entry = iter.next();
                        submissionFiles.put(Paths.get(entry.getKey()), entry.getValue().asText());
                    }
                }

                if (ccid == null) {
                    Optional<String> ccidCookie = Optional.ofNullable(uriInfo.getQueryParameters().getFirst("ccid"));
                    ccid = ccidCookie.orElse(Util.createPronouncableUID());
                }

                ObjectNode result = checkService.checkNJS(repo, problem, ccid, submissionFiles);
                return Response.ok(result).type(MediaType.APPLICATION_JSON).header("Set-Cookie", "ccid=" + ccid).build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
        }, executorService);
    }
}
