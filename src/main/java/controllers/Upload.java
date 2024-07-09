package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.script.ScriptException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.horstmann.codecheck.Plan;
import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.Report;
import com.horstmann.codecheck.Util;

import models.CodeCheck;

@Path("/upload")
public class Upload {
    final String repo = "ext";

    @Inject
    private CodeCheck codeCheck;

    @POST
    @Path("/files")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadFiles(@FormParam("request") String request) {
        return uploadFiles(request, com.horstmann.codecheck.Util.createPublicUID(), Util.createPrivateUID());
    }

    @POST
    @Path("/editedFiles/{problem}/{editKey}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response editedFiles(@FormParam("request") String request, @PathParam("problem") String problem, @PathParam("editKey") String editKey) {
        try {
            if (checkEditKey(problem, editKey))
                return uploadFiles(request, problem, editKey);
            else
                return Response.status(Response.Status.BAD_REQUEST).entity("Wrong edit key " + editKey + " in problem " + problem).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Problem not found: " + problem).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @Path("/files/{problem}/{editKey}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadFiles(@FormParam("request") String request, @PathParam("problem") String problem, @PathParam("editKey") String editKey) {
        try {
            if (problem == null)
                return Response.status(Response.Status.BAD_REQUEST).entity("No problem id").build();
            int n = 1;
            MultivaluedMap<String, String> params = Util.parseFormUrlEncoded(request);
            Map<Path, byte[]> problemFiles = new TreeMap<>();
            while (params.containsKey("filename" + n)) {
                String filename = params.getFirst("filename" + n);
                if (filename.trim().length() > 0) {
                    String contents = params.getFirst("contents" + n).replaceAll("\r\n", "\n");
                    problemFiles.put(Path.of(filename), contents.getBytes(StandardCharsets.UTF_8));
                }
                n++;
            }
            problemFiles.put(Path.of("edit.key"), editKey.getBytes(StandardCharsets.UTF_8));
            String response = checkAndSaveProblem(request, problem, problemFiles);
            return Response.ok(response).type(MediaType.TEXT_HTML).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @Path("/problem")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@FormParam("request") String request) {
        return uploadProblem(request, com.horstmann.codecheck.Util.createPublicUID(), Util.createPrivateUID());
    }

    @POST
    @Path("/editedProblem/{problem}/{editKey}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response editedProblem(@FormParam("request") String request, @PathParam("problem") String problem, @PathParam("editKey") String editKey) {
        try {
            if (checkEditKey(problem, editKey))
                return uploadProblem(request, problem, editKey);
            else
                return Response.status(Response.Status.BAD_REQUEST).entity("Wrong edit key " + editKey + " of problem " + problem).build();
        } catch (IOException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Problem not found: " + problem).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Util.getStackTrace(ex)).build();
        }
    }

    @POST
    @Path("/problem/{problem}/{editKey}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadProblem(@FormParam("request") String request, @PathParam("problem") String problem, @PathParam("editKey") String editKey) {
        try {
            FormDataMultiPart multiPart = Util.parseMultipartFormData(request);
            if (problem == null)
                return Response.status(Response.Status.BAD_REQUEST).entity("No problem id").build();
            FormDataBodyPart zipPart = multiPart.getField("file");
            InputStream fileInputStream = zipPart.getValueAs(InputStream.class);
            byte[] contents = Util.readAllBytes(fileInputStream);
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
    @Path("/editKeySubmit/{problem}/{editKey}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response editKeySubmit(@FormParam("request") String request, @PathParam("problem") String problem, @PathParam("editKey") String editKey) {
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
            return Response.ok(views.html.edit.render(problem, filesAndContents, correctEditKey, problemUrl)).build();
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

    private String checkAndSaveProblem(HttpServletRequest request, String problem, Map<Path, byte[]> problemFiles)
            throws IOException, InterruptedException, NoSuchMethodException, ScriptException {
        StringBuilder response = new StringBuilder();
        String report = null;
        if (problemFiles.containsKey(Path.of("tracer.js"))) {
            codeCheck.saveProblem("ext", problem, problemFiles);
        } else {
            report = codeCheck.checkAndSave(problem, problemFiles);
        }
        response.append(
                "<html><head><title></title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>");
        response.append("<body style=\"font-family: sans-serif\">");

        String prefix = models.Util.prefix(request) + "/";
        String problemUrl = createProblemUrl(request, problem, problemFiles);
        response.append("Public URL (for your students): ");
        response.append("<a href=\"" + problemUrl + "\" target=\"_blank\">" + problemUrl + "</a>");
        Path editKeyPath = Path.of("edit.key");
        if (problemFiles.containsKey(editKeyPath)) {
            String editKey = new String(problemFiles.get(editKeyPath), StandardCharsets.UTF_8);
            String editURL = prefix + "private/problem/" + problem + "/" + editKey;
            response.append("<br/>Edit URL (for you only): ");
            response.append("<a href=\"" + editURL + "\">" + editURL + "</a>");
        }
        if (report != null) {
            String run = Base64.getEncoder().encodeToString(report.getBytes(StandardCharsets.UTF_8));
            response.append(
                    "<br/><iframe height=\"400\" style=\"width: 90%; margin: 2em;\" src=\"data:text/html;base64," + run
                            + "\"></iframe>");
        }
        response.append("</li>\n");
        response.append("</ul><p></body></html>\n");
        return response.toString();
    }

    private String createProblemUrl(HttpServletRequest request, String problem, Map<Path, byte[]> problemFiles) {
        String type;
        if (problemFiles.containsKey(Path.of("tracer.js"))) {
            type = "tracer";
        } else {
            type = "files";
        }
        String prefix = models.Util.prefix(request) + "/";
        String problemUrl = prefix + type + "/" + problem;
        return problemUrl;
    }

    private static Path longestCommonPrefix(Path p, Path q) {
        if (p == null || q == null) return null;
        int i = 0;
        boolean matching = true;
        while (matching && i < Math.min(p.getNameCount(), q.getNameCount())) {
            if (p.getName(i).equals(q.getName(i))) i++;
            else matching = false;
        }
        return i == 0 ? null : p.subpath(0, i);
    }

    private static Map<Path, byte[]> fixZip(Map<Path, byte[]> problemFiles) throws IOException {
        Path r = null;
        boolean first = true;
        for (Path p : problemFiles.keySet()) {
            if (first) {
                r = p;
                first = false;
            } else r = longestCommonPrefix(r, p);
        }
        if (r == null) return problemFiles;
        Map<Path, byte[]> fixedProblemFiles = new TreeMap<>();
        if (problemFiles.keySet().size() == 1) {
            fixedProblemFiles.put(r.getFileName(), problemFiles.get(r));
        } else {
            for (Map.Entry<Path, byte[]> entry : problemFiles.entrySet()) {
                fixedProblemFiles.put(r.relativize(entry.getKey()), entry.getValue());
            }
        }

        return fixedProblemFiles;
    }
}
