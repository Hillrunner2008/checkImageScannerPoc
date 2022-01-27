package com.stackleader.check.ocr;

import static com.stackleader.check.ocr.Librarian.detect;
import static com.stackleader.check.ocr.Librarian.extractTessResources;
import java.io.File;
import java.util.Collections;
import java.util.Properties;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

@SpringBootApplication
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        final Properties properties = new Properties();
        detect(properties, Collections.<String>emptyList());
        SpringApplication.run(Main.class, args);
        LOG.debug("Spring Started");
    }

    @Bean(name = "multipartResolver")
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        multipartResolver.setMaxUploadSize(100000);
        return multipartResolver;
    }

    @Bean(name = "tesseract")
    public Tesseract tesseract(@Value("${tessData.dir}") String tessData) throws Exception {
        LOG.debug("tessData.dir={}", tessData);
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessData);
        tesseract.setLanguage("e13b");
        tesseract.setOcrEngineMode(3);
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO_OSD);
        return tesseract;
    }

}
