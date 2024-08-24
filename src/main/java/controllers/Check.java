package controllers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import services.CheckService;

@ApplicationScoped
@jakarta.ws.rs.Path("/")
public class Check {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private CheckService checkService;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @POST
    @jakarta.ws.rs.Path("/check")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response checkHTML(@Context HttpServletRequest request, MultivaluedMap<String, String> formParams) {
            try {
                Map<Path, String> submissionFiles = new TreeMap<>();
                String repo = "";
                String problem = "";
                String ccid = null;
                
                for (Map.Entry<String, List<String>> entry : formParams.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue().get(0);
                    if (key.equals("repo"))
                        repo = value;
                    else if (key.equals("problem"))
                        problem = value;
                    else if (!key.equals("ccid"))
                        ccid = value;
                    else
                        submissionFiles.put(Paths.get(key), value);
                }
                
                // Get the ccid from cookies
                if (ccid == null) {
                    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
                    if (cookies != null) {
                        for (jakarta.servlet.http.Cookie cookie : cookies) {
                            if ("ccid".equals(cookie.getName())) {
                                ccid = cookie.getValue();
                                break;
                            }
                        }
                    }
                    if (ccid == null) {
                        ccid = com.horstmann.codecheck.Util.createPronouncableUID();
                    }
                }
                
                String result = checkService.checkHTML(repo, problem, ccid, submissionFiles);

                // Create a NewCookie using Builder
                NewCookie newCookie = new NewCookie.Builder("ccid")
                        .value(ccid)
                        .path("/")
                        .maxAge(60 * 60) // 1 hour
                        .build();

                // Build response with the cookie
                Response.ResponseBuilder responseBuilder = Response.ok(result).type("text/html");
                responseBuilder.cookie(newCookie);

                return responseBuilder.build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
    }

    // Method for handling form-urlencoded data
    @POST
    @jakarta.ws.rs.Path("/run")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response runFormPost(MultivaluedMap<String, String> params) {
            try {
                // Handle form-urlencoded data here
                String result = checkService.runFormPost(params);
                return Response.ok(result).type(MediaType.TEXT_PLAIN).build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
    }

    // Method for handling multipart form data
    @POST
    @jakarta.ws.rs.Path("/run")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response runFileUpload(
        @Context PathSegment description,
        @Context PathSegment fileUpload) {

        try {
            byte[] fileContentBytes = fileUpload.getPath().getBytes();
            String fileContent = new String(fileContentBytes); // Read file content

            Path filePath = Paths.get(fileUpload.getPath());
            Map<Path, String> submissionFiles = Map.of(filePath, fileContent);
            String result = checkService.runFileUpload(description.getPath(), submissionFiles);
            return Response.ok(result).type(MediaType.TEXT_PLAIN).build();
        } catch (Exception ex) {
            return Response.serverError().entity(Util.getStackTrace(ex)).build();
        }
}

    // Method for handling JSON data
    @POST
    @jakarta.ws.rs.Path("/run")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response runJSON(JsonNode json) {
            try {
                ObjectNode resultNode = checkService.runJSON(json);  // Get the result as ObjectNode
                String result = resultNode.toString();  // Convert ObjectNode to JSON string
                return Response.ok(result).type(MediaType.APPLICATION_JSON).build();  // Send JSON response
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
    }

    @POST
    @jakarta.ws.rs.Path("/njs")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON })
    public Response checkNJS() {
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
                    json.fields().forEachRemaining(entry -> submissionFiles.put(Paths.get(entry.getKey()), entry.getValue().asText()));
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
    }
}
