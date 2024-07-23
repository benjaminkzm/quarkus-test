package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import com.horstmann.codecheck.Util;
import models.CodeCheck;
import views.EditHTML;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.RestForm;

@RequestScoped
@jakarta.ws.rs.Path("/upload")
public class Upload {

    final String repo = "ext";

    @Inject
    private CodeCheck codeCheck;

    @POST
    @jakarta.ws.rs.Path("/files")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadFiles(@FormParam("request") String request,
                                @FormParam("problem") String problem,
                                @FormParam("editKey") String editKey,
                                @Context HttpServletRequest httpServletRequest) {
        try {
            if (problem == null)
                return Response.status(Response.Status.BAD_REQUEST)
                               .entity("No problem id").build();

            Map<String, String[]> params = httpServletRequest.getParameterMap();
            Map<Path, byte[]> problemFiles = new TreeMap<>();
            int n = 1;
            while (params.containsKey("filename" + n)) {
                String filename = params.get("filename" + n)[0];
                if (filename.trim().length() > 0) {
                    String contents = params.get("contents" + n)[0].replaceAll("\r\n", "\n");
                    problemFiles.put(Path.of(filename), contents.getBytes(StandardCharsets.UTF_8));
                }
                n++;
            }
            problemFiles.put(Path.of("edit.key"), editKey.getBytes(StandardCharsets.UTF_8));

            String response = checkAndSaveProblem(request, problem, problemFiles);
            return Response.ok(response).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/editedFiles/{problem}/{editKey}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response editedFiles(@FormParam("request") String request,
                                @PathParam("problem") String problem,
                                @PathParam("editKey") String editKey,
                                @Context HttpServletRequest httpServletRequest) {
        try {
            if (checkEditKey(problem, editKey))
                return uploadFiles(request, problem, editKey, httpServletRequest);
            else
                return Response.status(Response.Status.BAD_REQUEST)
                               .entity("Wrong edit key " + editKey + " in problem " + problem).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Problem not found: " + problem).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/problem")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@RestForm("request") String request,
                                  @MultipartForm FileUploadForm form) {
        return uploadProblem(request, com.horstmann.codecheck.Util.createPublicUID(), Util.createPrivateUID(), form);
    }

    @POST
    @jakarta.ws.rs.Path("/editedProblem/{problem}/{editKey}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response editedProblem(@RestForm("request") String request,
                                  @PathParam("problem") String problem,
                                  @PathParam("editKey") String editKey,
                                  @MultipartForm FileUploadForm form) {
        try {
            if (checkEditKey(problem, editKey))
                return uploadProblem(request, problem, editKey, form);
            else
                return Response.status(Response.Status.BAD_REQUEST)
                               .entity("Wrong edit key " + editKey + " of problem " + problem).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Problem not found: " + problem).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/problem/{problem}/{editKey}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@RestForm("request") String request,
                                  @PathParam("problem") String problem,
                                  @PathParam("editKey") String editKey,
                                  @MultipartForm FileUploadForm form) {
        try {
            if (problem == null)
                return Response.status(Response.Status.BAD_REQUEST).entity("No problem id").build();
            byte[] contents = Util.readAllBytes(form.file);
            Map<Path, byte[]> problemFiles = Util.unzip(contents);
            problemFiles = fixZip(problemFiles);
            Path editKeyPath = Path.of("edit.key");
            if (!problemFiles.containsKey(editKeyPath))
                problemFiles.put(editKeyPath, editKey.getBytes(StandardCharsets.UTF_8));
            String response = checkAndSaveProblem(request, problem, problemFiles);
            return Response.ok(response).type(MediaType.TEXT_HTML).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/editKeySubmit/{problem}/{editKey}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response editKeySubmit(@FormParam("request") String request,
                                  @PathParam("problem") String problem,
                                  @PathParam("editKey") String editKey) {
        if (problem.equals(""))
            return Response.status(Response.Status.BAD_REQUEST).entity("No problem id").build();
        try {
            Map<Path, byte[]> problemFiles = codeCheck.loadProblem(repo, problem);
            Path editKeyPath = Path.of("edit.key");
            if (!problemFiles.containsKey(editKeyPath))
                return Response.status(Response.Status.BAD_REQUEST).entity("Wrong edit key " + editKey + " for problem " + problem).build();
            String correctEditKey = new String(problemFiles.get(editKeyPath), StandardCharsets.UTF_8);
            if (!editKey.equals(correctEditKey.trim())) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Wrong edit key " + editKey + " for problem " + problem).build();
            }
            Map<String, String> filesAndContents = new TreeMap<>();
            for (Map.Entry<Path, byte[]> entries : problemFiles.entrySet()) {
                Path p = entries.getKey();
                if (!p.getName(0).toString().equals("_outputs")) {
                    filesAndContents.put(p.toString(), new String(entries.getValue(), StandardCharsets.UTF_8));
                }
            }
            filesAndContents.remove("edit.key");
            String problemUrl = createProblemUrl(request, problem, problemFiles);
            String htmlContent = EditHTML.generateEditHtml(problem, filesAndContents, correctEditKey, problemUrl);
            return Response.ok(htmlContent).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Problem not found: " + problem).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
        }
    }

    private boolean checkEditKey(String problem, String editKey) throws IOException {
        Map<Path, byte[]> problemFiles = codeCheck.loadProblem(repo, problem);
        Path editKeyPath = Path.of("edit.key");
        if (problemFiles.containsKey(editKeyPath)) {
            String correctEditKey = new String(problemFiles.get(editKeyPath), StandardCharsets.UTF_8);
            return editKey.equals(correctEditKey.trim());
        } else return false;
    }

    private String checkAndSaveProblem(String request, String problem, Map<Path, byte[]> problemFiles)
            throws IOException, InterruptedException, NoSuchMethodException {
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

    private String createProblemUrl(String request, String problem, Map<Path, byte[]> problemFiles) {
        return request;
    }
}
