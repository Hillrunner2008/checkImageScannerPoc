package com.stackleader.check.ocr;

import com.google.common.primitives.Doubles;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author dcnorris
 */
@RestController
public class CheckOcrApi {
    
    private static final Logger LOG = LoggerFactory.getLogger(CheckOcrApi.class);

    @Autowired
    private OcrProcessor ocrProcessor;

    @PostMapping(value = "/processCheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public ToadLine processCheckImage(HttpServletRequest request) {
        try(BufferedReader reader = request.getReader()) {
            String contentLength = request.getHeader("Content-Length");
            if (Objects.nonNull(Doubles.tryParse(contentLength))) {
                LOG.info(contentLength);
            }
            final String encodedImageText = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            byte[] imageBytes = Base64.getDecoder().decode(encodedImageText.split(",")[1]);
            try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                BufferedImage checkImage = ImageIO.read(is);
                Word extractedToadLine = ocrProcessor.extractToadLine(checkImage);
                return new ToadLine(extractedToadLine.getText());
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            throw new IllegalStateException("Could not read image or extract required fields");
        }

    }
}
