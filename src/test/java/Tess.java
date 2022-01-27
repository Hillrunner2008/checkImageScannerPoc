

import com.recognition.software.jdeskew.ImageDeskew;
import com.recognition.software.jdeskew.ImageUtil;
import de.vorb.tesseract.tools.preprocessing.binarization.Otsu;
import de.vorb.tesseract.tools.preprocessing.binarization.Sauvola;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.LoadLibs;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

public class Tess {

    private static final int FIXED_SCROLL_DELAY = 4000;
    private static final Otsu OTSU = new Otsu();
    private static final Sauvola SAUVOLA = new Sauvola();

    public static void main(String[] args) throws Exception {
        File extractTessResources = LoadLibs.extractTessResources("linux-x86-64");
        System.setProperty("java.library.path", extractTessResources.getPath());
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(getTessdataDir().toString());
        tesseract.setLanguage("e13b");
        tesseract.setOcrEngineMode(3);
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO_OSD);

        // step 0
        BufferedImage image = ImageIO.read(new File("/home/dcnorris/Downloads/check_example_06.jpeg"));
        showModal("Step 1: Original Image", image);
        // step 1
        BufferedImage binary = convertToBinary(image);
        showModal("Step 2: binary Image", binary);
        // step 2
        BufferedImage imageDeSkew = imageDeSkew(binary);
        showModal("Step 3: Deskew Image", imageDeSkew);

        List<Word> words = tesseract.getWords(imageDeSkew, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);

        sortWords(words);
        Word toadLine = words.stream()
                .filter(word -> word.getConfidence() > 30)
                .filter(word -> word.getText().length() > 10)
                .filter(word -> word.getText().startsWith("C"))
                .findFirst().orElseThrow();
        System.out.println(toadLine.getText());
//        ToadLine toadLine;
    }

//    private static org.opencv.core.Mat bufferedImageToMat(BufferedImage bi) {
//        var mat = new Mat(bi.getHeight(), bi.getWidth(), CV_8UC(3));
//        Indexer indexer = mat.createIndexer();
//        for (int i = 0; i < bi.getHeight(); i++) {
//            for (int j = 0; j < bi.getWidth(); j++) {
//                var rgb = bi.getRGB(i, j);
//                indexer.index(j, i, 0, (rgb >> 0) & 0xFF);
//                indexer.index(j, i, 1, (rgb >> 8) & 0xFF);
//                indexer.index(j, i, 2, (rgb >> 16) & 0xFF);
//            }
//        }
//        org.opencv.core.Mat openCvMat = new org.opencv.core.Mat(mat.address());
//        return openCvMat;
//    }
//    def bufferedImageToMat = Flow[BufferedImage].map(bi => {
//  val mat = new Mat(bi.getHeight, bi.getWidth, CV_8UC(3))
//  val indexer:UByteRawIndexer = mat.createIndexer()
//  indexer.release()
//  mat
//})
//
//def matToBufferedImage = Flow[Mat].map(mat => {
//  Java2DFrameUtils.toBufferedImage(mat)
//})
    private static BufferedImage imageDeSkew(BufferedImage bi) {
        var deSkew = new ImageDeskew(bi);
        var imageSkewAngle = deSkew.getSkewAngle();
        final double skewThreshold = 0.05d;
        if (imageSkewAngle > skewThreshold || imageSkewAngle < -skewThreshold) {
            return ImageUtil.rotate(bi, -imageSkewAngle, bi.getWidth() / 2, bi.getHeight() / 2);
        }
        return bi;
    }

    private static BufferedImage convertToBinary(BufferedImage image) {
//        BufferedImage binary = ImageHelper.convertImageToBinary(image);
        BufferedImage binarize = SAUVOLA.binarize(image);
        return binarize;
    }

    public static Path getTessdataDir() {
        final String tessdataPrefix = "/home/dcnorris/NetBeansProjects/tesseractMICR/tessdata_best";
        Path tessdataDir;
        if (tessdataPrefix != null) {
            tessdataDir = Paths.get(tessdataPrefix).resolve("tessdata");

            if (Files.isDirectory(tessdataDir) && Files.isReadable(tessdataDir)) {
                return tessdataDir;
            }
        }

        tessdataDir = Paths.get("tessdata").toAbsolutePath();

        if (Files.isDirectory(tessdataDir) && Files.isReadable(tessdataDir)) {
            return tessdataDir;
        } else {
            return Paths.get("");
        }
    }

    public static void showModal(String label, BufferedImage image) {
        JLabel picLabel = new JLabel(new ImageIcon(image));
        JOptionPane.showMessageDialog(null, picLabel, label, JOptionPane.PLAIN_MESSAGE, null);
    }

    static Comparator<Word> yPosComparator = Comparator.comparingDouble(word -> word.getBoundingBox().getY());
    static Comparator<Word> widthComparator = yPosComparator.thenComparingDouble(word -> word.getBoundingBox().getWidth());

    private static void sortWords(List<Word> words) {
        words.sort(widthComparator.reversed());
    }

}
