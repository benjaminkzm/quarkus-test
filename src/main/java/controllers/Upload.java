package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import javax.script.ScriptException;

import org.apache.commons.text.StringEscapeUtils;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.horstmann.codecheck.Util;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import models.CodeCheck; // Import for escaping HTML

@RequestScoped
@jakarta.ws.rs.Path("/")
public class Upload {

    final String repo = "ext";

    @Inject
    private CodeCheck codeCheck;

    @POST
    @jakarta.ws.rs.Path("/uploadFiles")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadFiles(@FormParam("filename1") String filename1,
                                @FormParam("contents1") String contents1,
                                @FormParam("filename2") String filename2,
                                @FormParam("contents2") String contents2,
                                // Add additional FormParam annotations as needed
                                @jakarta.ws.rs.core.Context UriInfo uriInfo) {
        try {
            StringBuilder response = new StringBuilder("Processed request with problem: ");
            response.append(Util.createPublicUID());

            response.append("<br>Filename 1: ").append(StringEscapeUtils.escapeHtml4(filename1));
            response.append("<br>Contents 1: <pre>").append(StringEscapeUtils.escapeHtml4(contents1)).append("</pre>");
            if (filename2 != null && contents2 != null) {
                response.append("<br>Filename 2: ").append(StringEscapeUtils.escapeHtml4(filename2));
                response.append("<br>Contents 2: <pre>").append(StringEscapeUtils.escapeHtml4(contents2)).append("</pre>");
            }
            // Handle additional file names and contents here

            return Response.ok(response.toString()).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/uploadProblem")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@RestForm("problem") FileUpload fileUpload,
                                  @jakarta.ws.rs.core.Context UriInfo uriInfo,
                                  @jakarta.ws.rs.core.Context HttpHeaders headers) {
                if (fileUpload == null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("File upload parameter is missing").build();
                }
        return uploadProblem(Util.createPublicUID(), Util.createPrivateUID(), fileUpload, uriInfo, headers);
    }

    @POST
    @jakarta.ws.rs.Path("/uploadProblem/{problem}/{editKey}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@PathParam("problem") String problem,
                                  @PathParam("editKey") String editKey,
                                  @RestForm("problem") FileUpload fileUpload,
                                  @jakarta.ws.rs.core.Context UriInfo uriInfo,
                                  @jakarta.ws.rs.core.Context HttpHeaders headers) {
        try {
            if (problem == null || problem.isEmpty())
                return Response.status(Response.Status.BAD_REQUEST).entity("No problem id").build();
            byte[] contents = Files.readAllBytes(fileUpload.uploadedFile());
            Map<Path, byte[]> problemFiles = Util.unzip(contents);
            problemFiles = fixZip(problemFiles);
            Path editKeyPath = Path.of("edit.key");
            if (!problemFiles.containsKey(editKeyPath))
                problemFiles.put(editKeyPath, editKey.getBytes(StandardCharsets.UTF_8));
            String response = checkAndSaveProblem(problem, problemFiles);
            
            // Generate the problem URL
            String problemUrl = createProblemUrl(uriInfo, headers, problem, problemFiles);
            
            response += "<br><a href=\"" + problemUrl + "\">View Problem</a>";
            
            return Response.ok(response).type(MediaType.TEXT_HTML).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
        }
    }

    private String checkAndSaveProblem(String problem, Map<Path, byte[]> problemFiles)
            throws IOException, InterruptedException, NoSuchMethodException, ScriptException {
        StringBuilder response = new StringBuilder();
        String report = null;
        if (problemFiles.containsKey(Path.of("tracer.js"))) {
            codeCheck.saveProblem(repo, problem, problemFiles);
        } else {
            report = codeCheck.checkAndSave(problem, problemFiles);
        }
        response.append("<html><head><title></title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>");
        response.append("<body style=\"font-family: sans-serif\">");
        if (report != null) {
            response.append("<pre>");
            response.append(report);
            response.append("</pre>");
        } else {
            response.append("<p>Upload of problem ").append(problem).append(" was successful.</p>");
        }
        response.append("<p><a href=\"").append(problem).append("\">Go back</a></p>");
        response.append("</body></html>");
        return response.toString();
    }

    private Map<Path, byte[]> fixZip(Map<Path, byte[]> problemFiles) {
        Map<Path, byte[]> result = new TreeMap<>();
        for (Map.Entry<Path, byte[]> entry : problemFiles.entrySet()) {
            Path path = entry.getKey();
            byte[] contents = entry.getValue();
            if (path.getNameCount() > 1 && path.getName(0).toString().equals("_inputs")) {
                path = path.subpath(1, path.getNameCount());
            }
            result.put(path, contents);
        }
        return result;
    }

    // Method to create a problem URL based on the request and problem files
    private String createProblemUrl(UriInfo uriInfo, HttpHeaders headers, String problem, Map<Path, byte[]> problemFiles) {
        String type;
        if (problemFiles.containsKey(Path.of("tracer.js"))) {
            type = "tracer";
        } else {
            type = "files";
        }
        String prefix = models.Util.prefix(uriInfo, headers) + "/";
        String problemUrl = prefix + type + "/" + problem;
        return problemUrl;
    }
}
