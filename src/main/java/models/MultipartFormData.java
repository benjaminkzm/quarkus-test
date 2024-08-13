package models;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

import java.io.InputStream;

public class MultipartFormData {

    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)  // Specify the type of the file part
    private InputStream file;

    @FormParam("description")
    private String description;

    // Getters and Setters
    public InputStream getFile() {
        return file;
    }

    public void setFile(InputStream file) {
        this.file = file;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
