package controllers;

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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.MultivaluedMap;

import services.CheckService;
import models.MultipartFormData;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jboss.resteasy.plugins.providers.multipart.FormDataContentDisposition;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public Response run(MultipartFormDataInput input) {
        try {
            String contentType = headers.getHeaderString(HttpHeaders.CONTENT_TYPE);
            Map<Path, String> submissionFiles = new TreeMap<>();

            if (MediaType.APPLICATION_FORM_URLENCODED.equals(contentType)) {
                // Form-urlencoded processing
                Map<String, List<InputPart>> formData = input.getFormDataMap();
                List<InputPart> params = formData.get("params"); // Retrieve form fields
                if (params != null) {
                    for (InputPart inputPart : params) {
                        String key = "unknown"; // Default key or handle differently
                        String value = inputPart.getBodyAsString(); // Get form field value
                        submissionFiles.put(Paths.get(key), value);
                    }
                }
                return Response.ok(checkService.run(submissionFiles)).type(MediaType.TEXT_PLAIN).build();

            } else if (MediaType.MULTIPART_FORM_DATA.equals(contentType)) {
                // Process multipart form data
                Map<String, List<InputPart>> formData = input.getFormDataMap();
                
                // Process files
                List<InputPart> fileParts = formData.get("file"); // Get files part
                if (fileParts != null) {
                    for (InputPart inputPart : fileParts) {
                        InputStream inputStream = inputPart.getBody(InputStream.class, null);
                        // Use a placeholder or default name if necessary
                        Path filePath = Paths.get("uploaded_file"); 
                        String fileContent = new String(inputStream.readAllBytes());
                        submissionFiles.put(filePath, fileContent);
                    }
                }
                return Response.ok(checkService.run(submissionFiles)).type(MediaType.TEXT_PLAIN).build();

            } else if (MediaType.APPLICATION_JSON.equals(contentType)) {
                // Process JSON data
                String jsonString = uriInfo.getQueryParameters().getFirst("jsonBody");
                JsonNode json = new ObjectMapper().readTree(jsonString);
                return Response.ok(checkService.runJSON(json)).type(MediaType.APPLICATION_JSON).build();

            } else {
                return Response.serverError().entity("Bad content type").build();
            }
        } catch (Exception ex) {
            return Response.serverError().entity(ex.getMessage()).build();
        }
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
