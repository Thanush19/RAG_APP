import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * One-off asset generator for the Thagaval brand PNG (opaque off-white
 * background, green document glyph above a black wordmark).
 *
 * 1. knocks the near-white background out to transparency
 * 2. writes the transparent wordmark
 * 3. writes an adaptive-icon foreground: glyph only, centred, inside the
 *    72dp-of-108dp safe zone so no launcher mask clips it
 */
public class MakeIcon {

    public static void main(String[] args) throws Exception {
        BufferedImage raw = ImageIO.read(new File(args[0]));
        int w = raw.getWidth(), h = raw.getHeight();
        BufferedImage logo = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int minGlyphX = w, minGlyphY = h, maxGlyphX = -1, maxGlyphY = -1;
        int opaque = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = raw.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int mn = Math.min(r, Math.min(g, b));
                int mx = Math.max(r, Math.max(g, b));
                // Neutral + bright == background. Coloured ink keeps a low
                // whiteness even when light, so anti-aliased edges survive.
                float white = mn <= 205 ? 0f : (mn >= 246 ? 1f : (246 - mn) / 41f);
                if (mx - mn > 28) white *= 0.12f;
                int alpha = Math.max(0, Math.min(255, Math.round(255f * (1f - white))));
                logo.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                if (alpha > 40) {
                    opaque++;
                    if (x < minGlyphX) minGlyphX = x;
                    if (x > maxGlyphX) maxGlyphX = x;
                    if (y < minGlyphY) minGlyphY = y;
                    if (y > maxGlyphY) maxGlyphY = y;
                }
            }
        }
        System.out.println("source " + w + "x" + h + " ink=" + opaque + " bbox x="
                + minGlyphX + "-" + maxGlyphX + " y=" + minGlyphY + "-" + maxGlyphY);
        ImageIO.write(logo, "png", new File(args[1]));

        // Glyph band only: the wordmark is separated from the mark by a blank
        // row gap, so cut at the first run of empty rows rather than a guess.
        int[] rowInk = new int[h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((logo.getRGB(x, y) >>> 24) & 0xFF) > 40) rowInk[y]++;
            }
        }
        int bandBottom = h;
        int empty = 0;
        for (int y = (int) (h * 0.15); y < h; y++) {
            empty = rowInk[y] == 0 ? empty + 1 : 0;
            if (empty >= 12) {
                bandBottom = y - empty + 1;
                break;
            }
        }
        int gMinX = w, gMinY = h, gMaxX = -1, gMaxY = -1;
        for (int y = 0; y < bandBottom; y++) {
            for (int x = 0; x < w; x++) {
                if (((logo.getRGB(x, y) >>> 24) & 0xFF) > 40) {
                    if (x < gMinX) gMinX = x;
                    if (x > gMaxX) gMaxX = x;
                    if (y < gMinY) gMinY = y;
                    if (y > gMaxY) gMaxY = y;
                }
            }
        }
        int gw = gMaxX - gMinX + 1, gh = gMaxY - gMinY + 1;
        BufferedImage glyph = logo.getSubimage(gMinX, gMinY, gw, gh);
        System.out.println("glyph " + gw + "x" + gh);

        int canvas = Integer.parseInt(args[2]);
        double fill = Double.parseDouble(args[3]);
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, canvas, canvas);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double s = (canvas * fill) / Math.max(gw, gh);
        int dw = (int) Math.round(gw * s), dh = (int) Math.round(gh * s);
        g2.drawImage(glyph, (canvas - dw) / 2, (canvas - dh) / 2, dw, dh, null);
        g2.dispose();
        ImageIO.write(out, "png", new File(args[4]));
        System.out.println("wrote " + args[1] + " and " + args[4] + " mark=" + dw + "x" + dh
                + " of " + canvas + " (" + Math.round(100.0 * dw / canvas) + "%)");
    }
}
