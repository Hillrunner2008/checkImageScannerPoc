package com.stackleader.check.ocr;

import com.recognition.software.jdeskew.ImageDeskew;
import com.recognition.software.jdeskew.ImageUtil;
import de.vorb.tesseract.tools.preprocessing.binarization.Otsu;
import de.vorb.tesseract.tools.preprocessing.binarization.Sauvola;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author dcnorris
 */
@Component
public class OcrProcessor {

    private static final Otsu OTSU = new Otsu();
    private static final Sauvola SAUVOLA = new Sauvola();

    @Autowired
    private Tesseract tesseract;

    public Word extractToadLine(BufferedImage bi) {
        BufferedImage binary = convertToBinary(bi);
        BufferedImage imageDeSkew = imageDeSkew(binary);
        List<Word> words = tesseract.getWords(imageDeSkew, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
        sortWords(words);

        Word toadLine = words.stream()
                .filter(word -> word.getConfidence() > 30)
                .filter(word -> word.getText().length() > 10)
                .findFirst().orElse(retryFlippedImage(imageDeSkew));
        return toadLine;
    }

    private static BufferedImage imageDeSkew(BufferedImage bi) {
        var deSkew = new ImageDeskew(bi);
        var imageSkewAngle = deSkew.getSkewAngle();
        final double skewThreshold = 0.05d;
        if (imageSkewAngle > skewThreshold || imageSkewAngle < -skewThreshold) {
            return ImageUtil.rotate(bi, -imageSkewAngle, bi.getWidth() / 2, bi.getHeight() / 2);
        }
        return bi;
    }

    private static BufferedImage flipCheckImage(BufferedImage bi) {
        return ImageUtil.rotate(bi, 180, bi.getWidth() / 2, bi.getHeight() / 2);
    }

    private static BufferedImage convertToBinary(BufferedImage image) {
        BufferedImage binarize = SAUVOLA.binarize(image);
        return binarize;
    }

    static Comparator<Word> yPosComparator = Comparator.comparingDouble(word -> word.getBoundingBox().getY());
    static Comparator<Word> widthComparator = yPosComparator.thenComparingDouble(word -> word.getBoundingBox().getWidth());

    private static void sortWords(List<Word> words) {
        words.sort(widthComparator.reversed());
    }

    private Word retryFlippedImage(BufferedImage imageDeSkew) {
        List<Word> words = tesseract.getWords(flipCheckImage(imageDeSkew), ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
        sortWords(words);
        return words.stream()
                .filter(word -> word.getConfidence() > 30)
                .filter(word -> word.getText().length() > 10)
                .findFirst().orElseThrow();
    }
}
