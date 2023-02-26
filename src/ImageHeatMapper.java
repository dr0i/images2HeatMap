/*
 * Copyright 2023 Pascal Christoph (dr0i), Jeff Friesen and Torsten Heup.
 *
 * Licensed under the Apache License, Version 2.0 the "License";
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * This class describes and contains the entry point to the blending transition.
 * Blending algorithm is provided by Jeff Friesen, color saturation by Torsten Heup.
 */

public class ImageHeatMapper extends JFrame {

    static double weight = 0;

    public ImageHeatMapper(String inputImagesDir, double saturateFactor, String outputFileName) throws IOException, InterruptedException, InvocationTargetException {
        super("ImageHeatMapper - blend multiple images averaged into one image");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Set<String> fileSet = getFiles(inputImagesDir);

        // get first image
        BufferedImage bi1 = getFirstImage(inputImagesDir, fileSet);

        // get ImagePanel
        final ImagePanel ip = getImagePanel(bi1);

        //load and blend images
        BufferedImage bi_composed = blendAllImages(fileSet, bi1, ip);

        System.out.println("that is all. Wait two seconds, then saturate with factor " + saturateFactor + " and save as " + outputFileName);
        Thread.sleep(2000);
        // saturate blended result
        changeRGBSaturation(bi_composed, saturateFactor, ip); //i +weight) ;//- weight * i));

        // write blended image
        writeBlendedImage(outputFileName, bi_composed);

        System.out.println("Closing the program in 5 seconds...");
        Thread.sleep(5000);
        this.dispose();
    }

    /**
     * Application's entry point.
     *
     * @param args array of command-line arguments
     */

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        final String inputImagesDir = args.length >= 1 ? args[0] : "./";
        final double saturateFactor = args.length >= 2 ? Double.valueOf(args[1]) : 1;
        final String outputFileName = args.length >= 3 ? args[2] : "blended.png";
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new ImageHeatMapper(inputImagesDir, saturateFactor, outputFileName);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        t.start();
    }


    private void writeBlendedImage(String outputFileName, BufferedImage bi_composed) {
        try {
            BufferedImage bi = bi_composed;  // retrieve image
            File outputFile = new File(outputFileName);
            ImageIO.write(bi, "png", outputFile);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create an image panel capable of displaying entire image. The widths
     * of both images and the heights of both images must be identical.
     */
    private ImagePanel getImagePanel(BufferedImage bi1) {
        final ImagePanel ip = new ImagePanel();
        ip.setPreferredSize(new Dimension(bi1.getWidth(),
            bi1.getHeight()));
        getContentPane().add(ip, BorderLayout.NORTH);
        ip.setImage(bi1);
        setVisible(true);
        return ip;
    }

    /**
     * Loads first image - to have something to blend into.
     */
    private BufferedImage getFirstImage(final String inputImagesDir, final Set<String> fileSet) throws IOException {
        BufferedImage bi1;
        String image1fn = fileSet.stream().findFirst().get();
        System.out.println(new URL("file:///" + image1fn));
        ImageIcon ii1 = new ImageIcon(new URL("file://" + image1fn));
        bi1 = new BufferedImage(ii1.getIconWidth(), ii1.getIconHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi1.createGraphics();
        g2d.drawImage(ii1.getImage(), 0, 0, null);
        g2d.dispose();
        return bi1;
    }

    private BufferedImage blendAllImages(Set<String> fileSet, BufferedImage bi1, ImagePanel ip) throws MalformedURLException {
        Graphics2D g2d;
        double i = 2; // start with 2 (=> see "blend(...): two images beginning with blending 1/2(=>50%))
        BufferedImage bi_composed = null;
        for (String file : fileSet) {
            System.out.println(new URL("file://" + file));
            ImageIcon ii2 = new ImageIcon(new URL("file://" + file));
            final BufferedImage bi2;
            bi2 = new BufferedImage(ii2.getIconWidth(), ii2.getIconHeight(),
                BufferedImage.TYPE_INT_RGB);
            g2d = bi2.createGraphics();
            g2d.drawImage(ii2.getImage(), 0, 0, null);
            g2d.dispose();
            bi_composed = blend(bi2, bi1, 1 / i); // consecutive images weight lesser to compute the average of them
            i++;
            ip.setImage(bi_composed);
            bi1 = bi_composed;
            pack();
        }
        return bi_composed;
    }

    private Set<String> getFiles(String dir) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(dir))) {
            return stream
                .filter(f -> f.toString().endsWith("png"))
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.toSet());
        }
    }

    /**
     * Blend the contents of two BufferedImages according to a specified
     * weight.
     * <p>
     * <p/>
     * <hr/> Copyright 2008 Jeff Friesen
     * <p/>
     *
     * @param bi1    first BufferedImage
     * @param bi2    second BufferedImage
     * @param weight the fractional percentage of the first image to keep
     * @return new BufferedImage containing blended contents of BufferedImage
     * arguments
     */
    public BufferedImage blend(BufferedImage bi1, BufferedImage bi2,
                               double weight) {
        if (bi1 == null)
            throw new NullPointerException("bi1 is null");

        if (bi2 == null)
            throw new NullPointerException("bi2 is null");

        int width = bi1.getWidth();
        if (width != bi2.getWidth())
            throw new IllegalArgumentException("widths not equal");

        int height = bi1.getHeight();
        if (height != bi2.getHeight())
            throw new IllegalArgumentException("heights not equal");

        BufferedImage bi3 = new BufferedImage(width, height,
            BufferedImage.TYPE_INT_RGB);
        int[] rgbim1 = new int[width];
        int[] rgbim2 = new int[width];
        int[] rgbim3 = new int[width];

        for (int row = 0; row < height; row++) {
            bi1.getRGB(0, row, width, 1, rgbim1, 0, width);
            bi2.getRGB(0, row, width, 1, rgbim2, 0, width);

            for (int col = 0; col < width; col++) {
                int rgb1 = rgbim1[col];
                int r1 = (rgb1 >> 16) & 255;
                int g1 = (rgb1 >> 8) & 255;
                int b1 = rgb1 & 255;

                int rgb2 = rgbim2[col];
                int r2 = (rgb2 >> 16) & 255;
                int g2 = (rgb2 >> 8) & 255;
                int b2 = rgb2 & 255;

                int r3 = (int) (r1 * weight + r2 * (1.0 - weight));
                int g3 = (int) (g1 * weight + g2 * (1.0 - weight));
                int b3 = (int) (b1 * weight + b2 * (1.0 - weight));
                rgbim3[col] = (r3 << 16) | (g3 << 8) | b3;
            }

            bi3.setRGB(0, row, width, 1, rgbim3, 0, width);
        }

        return bi3;
    }

    /**
     * Changes the color saturation of the given RGB image.
     * <p>
     * <p/>
     * <hr/> Copyright 2006-2012 Torsten Heup
     * <p/>
     * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
     * the License. You may obtain a copy of the License at
     * <p/>
     * http://www.apache.org/licenses/LICENSE-2.0
     * <p/>
     *
     * @param image image expected to contain a 4 band rgba color model.
     * @param s     The factor with which the saturation value should be multiplied.
     * @param ip    the ImagePanel to draw to
     */
    public void changeRGBSaturation(final BufferedImage image, final double s, ImagePanel ip) {
        double RW = 0.3086;
        double RG = 0.6084;
        double RB = 0.0820;

        final double a = (1 - s) * RW + s;
        final double b = (1 - s) * RW;
        final double c = (1 - s) * RW;
        final double d = (1 - s) * RG;
        final double e = (1 - s) * RG + s;
        final double f = (1 - s) * RG;
        final double g = (1 - s) * RB;
        final double h = (1 - s) * RB;
        final double i = (1 - s) * RB + s;

        final int width = image.getWidth();
        final int height = image.getHeight();
        final double[] red = new double[width * height];
        final double[] green = new double[width * height];
        final double[] blue = new double[width * height];

        final WritableRaster raster = image.getRaster();
        raster.getSamples(0, 0, width, height, 0, red);
        raster.getSamples(0, 0, width, height, 1, green);
        raster.getSamples(0, 0, width, height, 2, blue);

        for (int x = 0; x < red.length; x++) {
            final double r0 = red[x];
            final double g0 = green[x];
            final double b0 = blue[x];
            red[x] = a * r0 + d * g0 + g * b0;
            green[x] = b * r0 + e * g0 + h * b0;
            blue[x] = c * r0 + f * g0 + i * b0;
        }

        raster.setSamples(0, 0, width, height, 0, red);
        raster.setSamples(0, 0, width, height, 1, green);
        raster.setSamples(0, 0, width, height, 2, blue);
        ip.setImage(image);
        pack();
    }
}

/**
 * This class describes a panel that displays a BufferedImage's contents.
 */

class ImagePanel extends JPanel {
    private BufferedImage bi;

    /**
     * Specify and paint a new BufferedImage.
     *
     * @param bi BufferedImage whose contents are to be painted
     */

    void setImage(BufferedImage bi) {
        this.bi = bi;
        repaint();
    }

    /**
     * Paint the image panel.
     *
     * @param g graphics context used to paint the contents of the current
     *          BufferedImage
     */

    public void paintComponent(Graphics g) {
        if (bi != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.drawImage(bi, null, 0, 0);
        }
    }
}
