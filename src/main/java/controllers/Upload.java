package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import javax.script.ScriptException;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.horstmann.codecheck.Util;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import models.CodeCheck;

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
    public Response uploadFiles(@jakarta.ws.rs.core.Context UriInfo uriInfo) {
        MultivaluedMap<String, String> formParams = uriInfo.getQueryParameters();
        String problem = Util.createPublicUID();
        String editKey = Util.createPrivateUID();

        if (problem == null || problem.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("No problem id").build();
        }

        return uploadFiles(formParams, problem, editKey);
    }

    public Response uploadFiles(MultivaluedMap<String, String> formParams,
                                String problem,
                                String editKey) {
        try {
            String response = "Processed request with problem: " + problem;
            for (Map.Entry<String, java.util.List<String>> e : formParams.entrySet()) {
                response += "\n" + e.getKey() + "->" + e.getValue();
            }
            return Response.ok(response).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/uploadProblem")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@RestForm("problem") FileUpload fileUpload) {
        return uploadProblem(Util.createPublicUID(), Util.createPrivateUID(), fileUpload);
    }

    @POST
    @jakarta.ws.rs.Path("/uploadProblem/{problem}/{editKey}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@PathParam("problem") String problem,
                                  @PathParam("editKey") String editKey,
                                  @RestForm("problem") FileUpload fileUpload) {
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
}
