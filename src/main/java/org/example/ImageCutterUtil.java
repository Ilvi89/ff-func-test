package org.example;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;


public class ImageCutterUtil {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static BufferedImage execute(BufferedImage originalImage, int cropX, int cropY, int cropWidth, int cropHeight) throws IOException {
        if (!(originalImage == null || cropX < 0 || cropY < 0 || cropWidth <= 0 || cropHeight <= 0)) {
            if (cropX + cropWidth >= originalImage.getWidth()) {
                cropWidth = originalImage.getWidth() - cropX;
            }
            if (cropY + cropHeight >= originalImage.getHeight()) {
                cropHeight = originalImage.getHeight() - cropY;
            }
            BufferedImage croppedImage = new BufferedImage(cropWidth, cropHeight, originalImage.getType());
            try {
                BufferedImage subImage = originalImage.getSubimage(cropX, cropY, cropWidth, cropHeight);
                croppedImage.getGraphics().drawImage(subImage, 0, 0, null);
                File tempFile = File.createTempFile("ImageCutterUtil-" + UUID.randomUUID(), ".jpg");
                ImageIO.write(croppedImage, "jpg", tempFile);
                tempFile.delete();
            } catch (Exception e) {
                System.err.println("Error occurred while cropping the image: " + e.getMessage());
                throw e;
            }
            return croppedImage;
        }
        return null;
    }
}
