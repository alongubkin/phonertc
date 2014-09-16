package com.dooble.phonertc;

import android.content.Context;
import android.graphics.Point;
import android.opengl.GLSurfaceView;

public class VideoGLView extends GLSurfaceView {
  private Point screenDimensions;

  public VideoGLView(Context c, Point screenDimensions) {
    super(c);
    this.screenDimensions = screenDimensions;
  }

  public void updateDisplaySize(Point screenDimensions) {
    this.screenDimensions = screenDimensions;
  }

  @Override
  protected void onMeasure(int unusedX, int unusedY) {
    // Go big or go home!
    setMeasuredDimension(screenDimensions.x, screenDimensions.y);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    setSystemUiVisibility(SYSTEM_UI_FLAG_HIDE_NAVIGATION |
        SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }
}