package models;

import org.jboss.resteasy.reactive.RestForm;

public class FileUploadForm {
    @RestForm("file")
    public byte[] file;

}
