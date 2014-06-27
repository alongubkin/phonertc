package com.dooble.phonertc;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import org.webrtc.VideoRenderer.I420Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.EnumMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A GLSurfaceView{,.Renderer} that efficiently renders YUV frames from local &
 * remote VideoTracks using the GPU for CSC.  Clients will want to call the
 * constructor, setSize() and updateFrame() as appropriate, but none of the
 * other public methods of this class are of interest to clients (only to system
 * classes).
 */
public class VideoStreamsView
    extends GLSurfaceView
    implements GLSurfaceView.Renderer {

  /** Identify which of the two video streams is being addressed. */
  public static enum Endpoint { LOCAL, REMOTE };

  private final static String TAG = "VideoStreamsView";
  private EnumMap<Endpoint, Rect> rects =
      new EnumMap<Endpoint, Rect>(Endpoint.class);
  private Point screenDimensions;
  // [0] are local Y,U,V, [1] are remote Y,U,V.
  private int[][] yuvTextures = { { -1, -1, -1}, {-1, -1, -1 }};
  private int posLocation = -1;
  private long lastFPSLogTime = System.nanoTime();
  private long numFramesSinceLastLog = 0;
  private FramePool framePool = new FramePool();
  // Accessed on multiple threads!  Must be synchronized.
  private EnumMap<Endpoint, I420Frame> framesToRender =
      new EnumMap<Endpoint, I420Frame>(Endpoint.class);

  public VideoStreamsView(Context c, Point screenDimensions) {
    super(c);
    this.screenDimensions = screenDimensions;
    setPreserveEGLContextOnPause(true);
    setEGLContextClientVersion(2);
    setRenderer(this);
    setRenderMode(RENDERMODE_WHEN_DIRTY);
  }

  /** Queue |frame| to be uploaded. */
  public void queueFrame(final Endpoint stream, I420Frame frame) {
    // Paying for the copy of the YUV data here allows CSC and painting time
    // to get spent on the render thread instead of the UI thread.
    abortUnless(framePool.validateDimensions(frame), "Frame too large!");
    final I420Frame frameCopy = framePool.takeFrame(frame).copyFrom(frame);
    boolean needToScheduleRender;
    synchronized (framesToRender) {
      // A new render needs to be scheduled (via updateFrames()) iff there isn't
      // already a render scheduled, which is true iff framesToRender is empty.
      needToScheduleRender = framesToRender.isEmpty();
      I420Frame frameToDrop = framesToRender.put(stream, frameCopy);
      if (frameToDrop != null) {
        framePool.returnFrame(frameToDrop);
      }
    }
    if (needToScheduleRender) {
      queueEvent(new Runnable() {
          public void run() {
            updateFrames();
          }
        });
    }
  }

  // Upload the planes from |framesToRender| to the textures owned by this View.
  private void updateFrames() {
    I420Frame remoteFrame = null;
    synchronized (framesToRender) {
      remoteFrame = framesToRender.remove(Endpoint.REMOTE);
    }
    if (remoteFrame != null) {
      texImage2D(remoteFrame, yuvTextures[1]);
      framePool.returnFrame(remoteFrame);
    }
    abortUnless(remoteFrame != null, "Nothing to render!");
    requestRender();
  }

  /** Inform this View of the dimensions of frames coming from |stream|. */
  public void setSize(Endpoint stream, int width, int height) {
    // Generate 3 texture ids for Y/U/V and place them into |textures|,
    // allocating enough storage for |width|x|height| pixels.
    int[] textures = yuvTextures[stream == Endpoint.LOCAL ? 0 : 1];
    GLES20.glGenTextures(3, textures, 0);
    for (int i = 0; i < 3; ++i) {
      int w = i == 0 ? width : width / 2;
      int h = i == 0 ? height : height / 2;
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, w, h, 0,
          GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
          GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }
    checkNoGLES2Error();
  }

  @Override
  protected void onMeasure(int unusedX, int unusedY) {
    // Go big or go home!
    setMeasuredDimension(screenDimensions.x, screenDimensions.y);
  }

  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
    checkNoGLES2Error();
  }

  @Override
  public void onDrawFrame(GL10 unused) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    drawRectangle(yuvTextures[1], remoteVertices);
    ++numFramesSinceLastLog;
    long now = System.nanoTime();
    if (lastFPSLogTime == -1 || now - lastFPSLogTime > 1e9) {
      double fps = numFramesSinceLastLog / ((now - lastFPSLogTime) / 1e9);
      Log.d(TAG, "Rendered FPS: " + fps);
      lastFPSLogTime = now;
      numFramesSinceLastLog = 1;
    }
    checkNoGLES2Error();
  }

  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    int program = GLES20.glCreateProgram();
    addShaderTo(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_STRING, program);
    addShaderTo(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_STRING, program);

    GLES20.glLinkProgram(program);
    int[] result = new int[] { GLES20.GL_FALSE };
    result[0] = GLES20.GL_FALSE;
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
    abortUnless(result[0] == GLES20.GL_TRUE,
        GLES20.glGetProgramInfoLog(program));
    GLES20.glUseProgram(program);

    GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "y_tex"), 0);
    GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_tex"), 1);
    GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "v_tex"), 2);

    // Actually set in drawRectangle(), but queried only once here.
    posLocation = GLES20.glGetAttribLocation(program, "in_pos");

    int tcLocation = GLES20.glGetAttribLocation(program, "in_tc");
    GLES20.glEnableVertexAttribArray(tcLocation);
    GLES20.glVertexAttribPointer(
        tcLocation, 2, GLES20.GL_FLOAT, false, 0, textureCoords);

    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    checkNoGLES2Error();
  }

  // Wrap a float[] in a direct FloatBuffer using native byte order.
  private static FloatBuffer directNativeFloatBuffer(float[] array) {
    FloatBuffer buffer = ByteBuffer.allocateDirect(array.length * 4).order(
        ByteOrder.nativeOrder()).asFloatBuffer();
    buffer.put(array);
    buffer.flip();
    return buffer;
  }

  // Upload the YUV planes from |frame| to |textures|.
  private void texImage2D(I420Frame frame, int[] textures) {
    for (int i = 0; i < 3; ++i) {
      ByteBuffer plane = frame.yuvPlanes[i];
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
      int w = i == 0 ? frame.width : frame.width / 2;
      int h = i == 0 ? frame.height : frame.height / 2;
      abortUnless(w == frame.yuvStrides[i], frame.yuvStrides[i] + "!=" + w);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, w, h, 0,
          GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, plane);
    }
    checkNoGLES2Error();
  }

  // Draw |textures| using |vertices| (X,Y coordinates).
  private void drawRectangle(int[] textures, FloatBuffer vertices) {
    for (int i = 0; i < 3; ++i) {
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
    }

    GLES20.glVertexAttribPointer(
        posLocation, 2, GLES20.GL_FLOAT, false, 0, vertices);
    GLES20.glEnableVertexAttribArray(posLocation);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    checkNoGLES2Error();
  }

  // Compile & attach a |type| shader specified by |source| to |program|.
  private static void addShaderTo(
      int type, String source, int program) {
    int[] result = new int[] { GLES20.GL_FALSE };
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
    abortUnless(result[0] == GLES20.GL_TRUE,
        GLES20.glGetShaderInfoLog(shader) + ", source: " + source);
    GLES20.glAttachShader(program, shader);
    GLES20.glDeleteShader(shader);
    checkNoGLES2Error();
  }

  // Poor-man's assert(): die with |msg| unless |condition| is true.
  private static void abortUnless(boolean condition, String msg) {
    if (!condition) {
      throw new RuntimeException(msg);
    }
  }

  // Assert that no OpenGL ES 2.0 error has been raised.
  private static void checkNoGLES2Error() {
    int error = GLES20.glGetError();
    abortUnless(error == GLES20.GL_NO_ERROR, "GLES20 error: " + error);
  }

  // Remote image should span the full screen.
  private static final FloatBuffer remoteVertices = directNativeFloatBuffer(
      new float[] { 1, -1, -1, -1, 1, 1, -1, 1 });

  // Texture Coordinates mapping the entire texture.
  private static final FloatBuffer textureCoords = directNativeFloatBuffer(
      new float[] { 0, 0, 0, 1, 1, 0, 1, 1 });

  // Pass-through vertex shader.
  private static final String VERTEX_SHADER_STRING =
      "varying vec2 interp_tc;\n" +
      "\n" +
      "attribute vec4 in_pos;\n" +
      "attribute vec2 in_tc;\n" +
      "\n" +
      "void main() {\n" +
      "  gl_Position = in_pos;\n" +
      "  interp_tc = in_tc;\n" +
      "}\n";

  // YUV to RGB pixel shader. Loads a pixel from each plane and pass through the
  // matrix.
  private static final String FRAGMENT_SHADER_STRING =
      "precision mediump float;\n" +
      "varying vec2 interp_tc;\n" +
      "\n" +
      "uniform sampler2D y_tex;\n" +
      "uniform sampler2D u_tex;\n" +
      "uniform sampler2D v_tex;\n" +
      "\n" +
      "void main() {\n" +
      "  float y = texture2D(y_tex, interp_tc).r;\n" +
      "  float u = texture2D(u_tex, interp_tc).r - .5;\n" +
      "  float v = texture2D(v_tex, interp_tc).r - .5;\n" +
      // CSC according to http://www.fourcc.org/fccyvrgb.php
      "  gl_FragColor = vec4(y + 1.403 * v, " +
      "                      y - 0.344 * u - 0.714 * v, " +
      "                      y + 1.77 * u, 1);\n" +
      "}\n";
}