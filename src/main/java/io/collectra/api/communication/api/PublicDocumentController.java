package io.collectra.api.communication.api;

import io.collectra.api.communication.application.MessageDocumentLinkService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/documents")
public class PublicDocumentController {
    private final MessageDocumentLinkService links;

    public PublicDocumentController(MessageDocumentLinkService links) {
        this.links = links;
    }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        var document = links.open(token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.mediaType()));
        headers.setContentDisposition(ContentDisposition.inline().filename("document.pdf").build());
        headers.setCacheControl("private, no-store, max-age=0");
        return ResponseEntity.ok().headers(headers).body(document.content());
    }
}
