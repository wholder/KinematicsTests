import javafx.geometry.Point2D;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

// From: http://www.ryanjuckett.com/programming/analytic-two-bone-ik-in-2d/

public class ComputeIK extends JPanel {
  private static final double     epsilon = 0.0001;   // used to prevent division by small numbers
  private static final Dimension  size = new Dimension(620, 620);
  private final Bone              upper;              // Upper arm
  private final Bone              lower;              // Lower Arm
  private final double            armLength;
  private       JSlider           zoom;
  private       boolean           mouseDown, zooming, noZoom;

  private boolean updateIk (Point2D target) {
    double targetX = target.getX();
    double targetY = target.getY();
    double angle2;
    double targetDistSqr = (targetX * targetX + targetY * targetY);
    double sinAngle2;
    double cosAngle2;
    double cosAngle2_denom = 2 * upper.length * lower.length;
    if (cosAngle2_denom > epsilon) {
      cosAngle2 = (targetDistSqr - upper.length * upper.length - lower.length * lower.length) / (cosAngle2_denom);
      // if our result is not in the legal cosine range, we can not find a legal solution for the target
      if ((cosAngle2 < -1.0) || (cosAngle2 > 1.0)) {
        return  false;
      }
      // clamp our value into range so we can calculate the best solution when there are no valid ones
      cosAngle2 = Math.max(-1, Math.min(1, cosAngle2));
      angle2 = Math.acos(cosAngle2);
      // adjust for the desired bend direction
      sinAngle2 = Math.sin(angle2);
    } else {
      // At least one of the bones had a zero length. This means our solvable domain is a circle around the
      // origin with a radius equal to the sum of our bone lengths.
      double totalLenSqr = (upper.length + lower.length) * (upper.length + lower.length);
      if (targetDistSqr < (totalLenSqr - epsilon) || targetDistSqr > (totalLenSqr + epsilon)) {
        return  false;
      }
      // Only the value of angle1 matters at this point. We can just set angle2 to zero.
      angle2 = 0.0;
      cosAngle2 = 1.0;
      sinAngle2 = 0.0;
    }
    // Compute the value of angle1 based on the sine and cosine of angle2
    double triAdjacent = upper.length + lower.length * cosAngle2;
    double triOpposite = lower.length * sinAngle2;
    double tanY = targetY * triAdjacent - targetX * triOpposite;
    double tanX = targetX * triAdjacent + targetY * triOpposite;
    // Note that it is safe to call atan2(0,0) which will happen if targetX and targetY are zero
    double angle1 = Math.atan2(tanY, tanX);
    upper.setAngle(Math.toDegrees(angle1));
    lower.setAngle(Math.toDegrees(angle2));
    return true;
  }

  class Bone {
    public final double   length;
    private final Bone    parent;
    private final Color   color;
    private       double  angle;
    private       JSlider slider;

    Bone (double length, double angle, Bone parent, Color color) {
      this.length = length;
      this.angle = angle;
      this.parent = parent;
      this.color = color;
    }

    void setAngle (double angle) {
      this.angle = angle;
      if (slider != null) {
        slider.setValue((int) Math.round(angle));
      }
    }

    JSlider getSlider (ComputeIK view, int min, int max) {
      slider = new JSlider(JSlider.VERTICAL, min, max, (int) angle);
      slider.setForeground(color);
      slider.setBackground(color);
      slider.setMajorTickSpacing(45);
      slider.setMinorTickSpacing(5);
      slider.setPaintTicks(true);
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(ev -> {
        if (!mouseDown && !zooming) {
          setAngle(slider.getValue());
          updateZoomSlider();
          view.repaint();
        }
      });
      return slider;
    }

    void draw (Graphics2D g2) {
      Point2D base = parent != null ? parent.getEndOffset() : new Point2D(0, 0);
      Point2D end = base.add(getEndOffset());
      g2.setColor(color);
      g2.draw(new Line2D.Double(base.getX(), base.getY(), end.getX(), end.getY()));
      g2.fill(new Ellipse2D.Double(base.getX() - 15, base.getY() - 15, 30, 30));
      g2.setColor(Color.WHITE);
      Font font = new Font("Helvetica", Font.BOLD, 12);
      GlyphVector gv = font.createGlyphVector(g2.getFontRenderContext(), Integer.toString((int) angle));
      Rectangle2D bnds = gv.getVisualBounds();
      AffineTransform af = AffineTransform.getTranslateInstance(base.getX() - bnds.getWidth() / 2, base.getY() - bnds.getHeight() / 2);
      af.scale(1, -1);
      g2.fill(af.createTransformedShape(gv.getOutline()));
    }

    Point2D getEndOffset () {
      Point2D vector = new Point2D(1, 0);
      Transform rot = new Rotate(parent != null ? parent.angle + angle : angle);
      vector = rot.transform(vector).normalize();
      return vector.multiply(length);
    }
  }

  @Override
  public void paint (Graphics gg) {
    Graphics2D g2 = (Graphics2D) gg;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    AffineTransform af = new AffineTransform();
    af.setToTranslation((double) size.width / 2, (double) size.height / 2);
    af.scale(1, -1);
    g2.setTransform(af);
    g2.setColor(Color.LIGHT_GRAY);
    g2.fill(new Ellipse2D.Double(-armLength, -armLength, armLength * 2, armLength * 2));
    // Draw reach line
    g2.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    Point2D end = getEndPoint();
    g2.setColor(Color.MAGENTA);
    g2.draw(new Line2D.Double(0, 0, end.getX(), end.getY()));
    // Draw bones
    g2.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    upper.draw(g2);
    lower.draw(g2);
    // Print upper and lower angles
    af.setToTranslation(0, 0);
    af.scale(1, 1);
    g2.setTransform(af);
    g2.setColor(Color.DARK_GRAY);
    drawString(g2, "Upper: " + String.format("%3.3f", upper.angle), 10, 20);
    drawString(g2, "Lower: " + String.format("%3.3f", lower.angle), 10, 45);
  }

  private void drawString (Graphics2D g2, String text, double x, double y) {
    Font font = new Font("Helvetica", Font.BOLD, 16);
    GlyphVector gv = font.createGlyphVector(g2.getFontRenderContext(), text);
    AffineTransform af = AffineTransform.getTranslateInstance(x, y);
    g2.fill(af.createTransformedShape(gv.getOutline()));
  }

  Point2D getRelPoint (int mx, int my) {
    double targetX = mx - (double) size.width / 2;
    double targetY = -my + (double) size.height / 2;
    return new Point2D(targetX, targetY);
  }

  Point2D getEndPoint () {
    Point2D end = upper.getEndOffset();
    end = end.add(lower.getEndOffset()).normalize();
    end = end.multiply(armLength);
    return end;
  }

  void updateZoomSlider () {
    noZoom = true;
    Point2D end = upper.getEndOffset();
    end = end.add(lower.getEndOffset());
    double value = end.magnitude();
    value /= armLength;
    value *= 100;
    zoom.setValue((int) value);
    noZoom = false;
  }

  ComputeIK () {
    setPreferredSize(size);
    upper = new Bone(150, 150, null, Color.RED);
    lower = new Bone(150, 150, upper, Color.BLUE);
    armLength = upper.length + lower.length;
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed (MouseEvent ev) {
        super.mousePressed(ev);
        mouseDown = true;
        if (updateIk(getRelPoint(ev.getX(), ev.getY()))) {
          updateZoomSlider();
          repaint();
        }
      }

      @Override
      public void mouseReleased (MouseEvent e) {
        super.mouseReleased(e);
        mouseDown = false;
      }
    });
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseDragged (MouseEvent ev) {
        super.mouseDragged(ev);
        if (updateIk(getRelPoint(ev.getX(), ev.getY()))) {
          updateZoomSlider();
          repaint();
        }
      }
    });
  }

  JSlider getZoomSlider () {
    zoom = new JSlider(JSlider.VERTICAL, 1, 100, 50);
    zoom.setForeground(Color.BLACK);
    zoom.setMajorTickSpacing(10);
    zoom.setMinorTickSpacing(1);
    zoom.setPaintTicks(true);
    zoom.setPaintLabels(true);
    zoom.addChangeListener(ev -> {
      if (!mouseDown && !noZoom) {
        zooming = true;
        int value = zoom.getValue();
        Point2D end = getEndPoint();
        end = end.multiply(((double) value / 100d));
        updateIk(end);
        zooming = false;
        repaint();
      }
    });
    return zoom;
  }

  private static JPanel addLegend (JComponent comp, String legend) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(comp, BorderLayout.CENTER);
    panel.add(new JLabel(legend, SwingConstants.CENTER), BorderLayout.NORTH);
    return panel;
  }

  public static void main (String[] args) {
    JFrame frame = new JFrame("Analytic Two Bone Inverse Kinematics");
    ComputeIK view = new ComputeIK();
    frame.add(view, BorderLayout.CENTER);
    JPanel controls = new JPanel(new GridLayout(1,3));
    JSlider j1 = view.upper.getSlider(view, 0, 360);
    JSlider j2 = view.lower.getSlider(view, -180, 180);
    controls.add(addLegend(j1, "Upper"));
    controls.add(addLegend(j2, "Lower"));
    controls.add(addLegend(view.getZoomSlider(), "Zoom"));
    view.updateZoomSlider();
    frame.add(controls, BorderLayout.EAST);
    frame.pack();
    frame.setResizable(false);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing (WindowEvent ev) {
        System.exit(0);
      }
    });
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}
