package org.lucoenergia.conluz.infrastructure.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A file answered as a download, with its own media type.
 *
 * <p>The application forces UTF-8 on every response ({@code server.servlet.encoding.force}), which would label a
 * binary file {@code application/zip;charset=UTF-8}. The forced encoding is cleared here, so a file carries a charset
 * only when its media type names one, as {@code text/csv;charset=UTF-8} does. It is cleared only once the file has
 * been produced, so an error answered instead still gets the forced UTF-8.
 */
public final class FileDownload {

    private FileDownload() {
    }

    public static ResponseEntity<byte[]> attachment(HttpServletResponse response, String filename, MediaType mediaType,
                                                    byte[] content) {
        response.setCharacterEncoding(null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}
