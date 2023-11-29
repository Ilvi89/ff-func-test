package org.example;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageUtils {
    public static BufferedImage rotateImage(BufferedImage image, int orientation) {
        assert image != null;
        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 0:
            case 1:
                break;
            case 2:
                transform.scale(-1, 1);
                transform.translate(-image.getWidth(), 0);
                break;
            case 3:
                transform.translate(image.getWidth(), image.getHeight());
                transform.rotate(Math.PI);
                break;
            case 4:
                transform.scale(1, -1);
                transform.translate(0, -image.getHeight());
                break;
            case 5:
                transform.rotate(Math.PI / 2);
                transform.scale(1, -1);
                break;
            case 6:
                transform.rotate(Math.PI / 2);
                transform.translate(0, -image.getHeight());
                break;
            case 7:
                transform.rotate(-Math.PI / 2);
                transform.scale(1, -1);
                transform.translate(-image.getWidth(), 0);
                break;
            case 8:
                transform.rotate(-Math.PI / 2);
                transform.translate(-image.getWidth(), 0);
                break;
            default:
                throw new RuntimeException("Invalid orientation value: " + orientation);
        }
        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        int width = image.getWidth();
        int height = image.getHeight();
        if (orientation > 4) {
            width = image.getHeight();
            height = image.getWidth();
        }
        BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        return op.filter(image, newImage);
    }

    public static int getOrientation(File file) {
        int orientation = 1;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null) {
                orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (ImageProcessingException | MetadataException | IOException ignored) {
        }
        return orientation;
    }

    public static BufferedImage resizeImage(File image, int newWidth) throws IOException {
        var o = getOrientation(image);
        BufferedImage as = rotateImage(ImageIO.read(image), o);
        double aspectRatio = ImageUtils.getAspectRatio(as);
        int newHeight = (int) (newWidth / aspectRatio);
        return ImageUtils.resizeImage(image, newWidth, newHeight);
    }

    public static double getAspectRatio(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        return (double) width / height;
    }


    public static BufferedImage resizeImage(File input, int targetWidth, int targetHeight) throws IOException {
        var o = getOrientation(input);
        BufferedImage inputFile = rotateImage(ImageIO.read(input), o);

        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, inputFile.getType());

        Graphics2D g2d = outputImage.createGraphics();
        g2d.drawImage(inputFile, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();


        return outputImage;
    }

    public static BufferedImage resizeImage(File image, boolean b, int newHeight) throws IOException {
        var o = getOrientation(image);
        BufferedImage as = rotateImage(ImageIO.read(image), o);
        double aspectRatio = ImageUtils.getAspectRatio(as);
        int newWidth = (int) (newHeight / aspectRatio);
        return ImageUtils.resizeImage(image, newWidth, newHeight);
    }
}
