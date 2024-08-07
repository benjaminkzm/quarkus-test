package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;

import models.CodeCheck;

@ApplicationScoped
public class Check {

    @Inject
    CodeCheck codeCheck;

    @Inject
    Executor executor;

    @POST
    @jakarta.ws.rs.Path("/checkHTML")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public CompletableFuture<Response> checkHTML(
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            @FormParam("repo") String repo,
            @FormParam("problem") String problem,
            @FormParam("ccid") String ccid,
            @RestForm String description,
            @RestForm FileUpload file) {

        return CompletableFuture.supplyAsync(() -> {
            String localCcid = (ccid != null) ? ccid : Util.createPronouncableUID();
            long startTime = System.nanoTime();
            Map<Path, String> submissionFiles = new TreeMap<>();
            if (file != null) {
                try {
                    String contents = new String(file.get().readAllBytes(), StandardCharsets.UTF_8);
                    Path filePath = Paths.get("uploaded-file");
                    submissionFiles.put(filePath, contents);
                } catch (Exception e) {
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("File read error").build();
                }
            }

            try {
                String report = codeCheck.run("html", repo, problem, localCcid, submissionFiles);
                double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
                if (report == null || report.isEmpty()) {
                    report = String.format("Timed out after %5.0f seconds\n", elapsed);
                }

                return Response.ok(report).header("Set-Cookie", "ccid=" + localCcid + "; Path=/").build();
            } catch (Exception ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
            }
        }, executor);
    }

    @POST
    @jakarta.ws.rs.Path("/run")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_JSON })
    @Produces(MediaType.TEXT_PLAIN)
    public CompletableFuture<Response> run(
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            @RestForm String description,
            @RestForm FileUpload file,
            @RestForm JsonNode json) {

        return CompletableFuture.supplyAsync(() -> {
            Map<Path, String> submissionFiles = new TreeMap<>();
            String contentType = headers.getHeaderString("Content-Type");

            try {
                if (MediaType.MULTIPART_FORM_DATA.equals(contentType) && file != null) {
                    String contents = new String(file.get().readAllBytes(), StandardCharsets.UTF_8);
                    Path filePath = Paths.get("uploaded-file");
                    submissionFiles.put(filePath, contents);
                } else if (MediaType.APPLICATION_JSON.equals(contentType) && json != null) {
                    Iterator<Entry<String, JsonNode>> iter = json.fields();
                    while (iter.hasNext()) {
                        Entry<String, JsonNode> entry = iter.next();
                        Path filePath = Paths.get(entry.getKey());
                        submissionFiles.put(filePath, entry.getValue().asText());
                    }
                }

                long startTime = System.nanoTime();
                String report = codeCheck.run("Text", submissionFiles);
                double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
                if (report == null || report.isEmpty()) {
                    report = String.format("Timed out after %5.0f seconds\n", elapsed);
                }

                return Response.ok(report).build();
            } catch (Exception ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
            }
        }, executor);
    }

    @POST
    @jakarta.ws.rs.Path("/checkNJS")
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON })
    @Produces(MediaType.APPLICATION_JSON)
    public CompletableFuture<Response> checkNJS(
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            @RestForm JsonNode json) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, String[]> params = new HashMap<>();
            try {
                if (headers.getHeaderString("Content-Type") == null || MediaType.APPLICATION_FORM_URLENCODED.equals(headers.getHeaderString("Content-Type"))) {
                    params = uriInfo.getQueryParameters().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> e.getValue().toArray(new String[0])
                            ));
                } else if (MediaType.APPLICATION_JSON.equals(headers.getHeaderString("Content-Type")) && json != null) {
                    Iterator<Entry<String, JsonNode>> iter = json.fields();
                    while (iter.hasNext()) {
                        Entry<String, JsonNode> entry = iter.next();
                        params.put(entry.getKey(), new String[]{entry.getValue().asText()});
                    }
                }

                String ccid = Optional.ofNullable(params.get("ccid")).map(v -> v[0]).orElse(Util.createPronouncableUID());
                String repo = Optional.ofNullable(params.get("repo")).map(v -> v[0]).orElse("ext");
                String problem = Optional.ofNullable(params.get("problem")).map(v -> v[0]).orElse(null);
                String reportType = "NJS";
                String scoreCallback = Optional.ofNullable(params.get("scoreCallback")).map(v -> v[0]).orElse(null);
                StringBuilder requestParams = new StringBuilder();
                ObjectNode studentWork = JsonNodeFactory.instance.objectNode();
                Map<Path, String> submissionFiles = new TreeMap<>();
                Map<Path, byte[]> reportZipFiles = new TreeMap<>();

                for (String key : params.keySet()) {
                    String value = params.get(key)[0];

                    if (requestParams.length() > 0) requestParams.append(", ");
                    requestParams.append(key).append("=");
                    int nl = value.indexOf('\n');
                    if (nl >= 0) {
                        requestParams.append(value.substring(0, nl)).append("...");
                    } else {
                        requestParams.append(value);
                    }

                    Path filePath = Paths.get(key);
                    submissionFiles.put(filePath, value);
                    reportZipFiles.put(filePath, value.getBytes(StandardCharsets.UTF_8));
                    studentWork.put(key, value);
                }

                String report = codeCheck.run(reportType, repo, problem, ccid, submissionFiles);
                ObjectNode result = (ObjectNode) new ObjectMapper().readTree(report);
                String reportHTML = result.get("report").asText();
                reportZipFiles.put(Paths.get("report.html"), reportHTML.getBytes(StandardCharsets.UTF_8));

                byte[] reportZipBytes = codeCheck.signZip(reportZipFiles);
                String reportZip = Base64.getEncoder().encodeToString(reportZipBytes);

                if (scoreCallback != null) {
                    if (scoreCallback.startsWith("https://")) {
                        scoreCallback = "http://" + scoreCallback.substring("https://".length());
                    }

                    ObjectNode augmentedResult = result.deepCopy();
                    augmentedResult.set("studentWork", studentWork);

                    String resultText = augmentedResult.toString();
                    Util.httpPost(scoreCallback, resultText, MediaType.APPLICATION_JSON);
                }

                result.put("zip", reportZip);
                return Response.ok(result).header("Set-Cookie", "ccid=" + ccid + "; Path=/").build();
            } catch (Exception ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
            }
        }, executor);
    }

}
