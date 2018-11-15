////////////////////////////////////////////////////////////////////////////////////
//
// ViewFinder - a simple Android app to:
//	  (i) read camera & show preview image on screen,
//	 (ii) compute some simple statistics from preview image and
//	(iii) superimpose results on screen in text and graphic form
// Based originally on http://web.stanford.edu/class/ee368/Android/ViewfinderEE368/
//
////////////////////////////////////////////////////////////////////////////////////

package com.example.viewfinder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// ----------------------------------------------------------------------

public class MainActivity extends Activity {
    String TAG = "ViewFinder";		// tag for logcat output
    String asterisks = " *******************************************"; // for noticable marker in log
	protected static int mCam = 0;      //  (0 => rear facing) the number of the camera to use
//	protected static int mCam = 1;      //  (1 => front facing) TODO: remove before release
    protected static Camera mCamera = null;
    private int nPixels = 480 * 640;      // approx number of pixels desired in preview
    protected static int mCameraHeight;   // preview height (determined later)
    protected static int mCameraWidth;    // preview width  (determined later)
    protected static Preview mPreview;
    protected static DrawOnTop mDrawOnTop;
    protected static LayoutParams mLayoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    private static boolean DBG = BuildConfig.DEBUG; // provide normal log output only in debug version

// for later: optional dump of useful info into the log
    static boolean bDisplayInfoFlag = false;	// show info about display  in log file
    static boolean nCameraInfoFlag = false;	// show info about cameras in log file

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.v(TAG, "onCreate" + asterisks);
        if (!checkCameraHardware(this)) {    // (need "context" as argument here)
            Log.e(TAG, "Device does not have a camera! Exiting"); // tablet perhaps?
            System.exit(0);    // finish()
        }
        if (DBG) Log.v(TAG, "Build.VERSION.SDK_INT " + Build.VERSION.SDK_INT);
//		setContentView(R.layout.activity_main); // the usual way to get window, but see onResume(...)
        // go full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // and hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getPermissions();   // NOTE: can *not* assume we actually have permissions after this call

//		for later: optional dump of useful info into the log
		if (bDisplayInfoFlag) ExtraInfo.showDisplayInfo(this); // show some info about display
		if (nCameraInfoFlag) ExtraInfo.showCameraInfoAll(); // show some info about all cameras
    }

    // Because the CameraDevice object is not a shared resource,
    // it's very important to release it when the activity is paused.

    @Override
    protected void onPause () {
        super.onPause();
        if (DBG) Log.v(TAG, "onPause" + asterisks);
        releaseCamera(mCam, true);    // release camera here
    }

    // which means the CameraDevice has to be (re-)opened when the activity is (re-)started
    // (as long as we have permission to use the camera)

    @Override
    protected void onResume () {
        super.onResume();
        if (DBG) Log.v(TAG, "onResume" + asterisks);
		super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);	// TODO: remove this before
        // release
        if (bCameraPermissionGranted) {
            openCamera(mCam);    // (re-)open camera here
            getPreviewSize(mCamera, nPixels);    // pick an available preview size

            // Create our DrawOnTop view.
            mDrawOnTop = new DrawOnTop(this);
            // Create our Preview view
            mPreview = new Preview(this, mDrawOnTop);
            // and set preview as the content of our activity.
            setContentView(mPreview);
            // and add overlay to content of our activity.
            addContentView(mDrawOnTop, mLayoutParams);
        }
    }

    @Override
    protected void onDestroy () {
        super.onDestroy();
        if (DBG) Log.v(TAG, "onDestroy" + asterisks);
        if (mCamera != null) releaseCamera(mCam, true);    // just in case it hasn't been released yet...
    }

//////////////////////////////////////////////////////////////////////////////

    // Check if this device actually has a camera!
    public static boolean checkCameraHardware (Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    //Used to open camera
    protected static void openCamera (int nCam) {
        String TAG = "openCamera";
        if (mCamera == null) {
            try {
                if (DBG) Log.i(TAG, "Opening camera " + nCam);
                mCamera = Camera.open(nCam);
            } catch (Exception e) {
                Log.e(TAG, "ERROR: camera open exception " + e);
                e.printStackTrace();
                System.exit(0); // should not happen
            }
        } else Log.e(TAG, "Camera already open");   // should not happen
    }

    protected static void releaseCamera (int nCam, boolean previewFlag) {
        String TAG = "releaseCamera";
        if (mCamera != null) {
            if (DBG) Log.i(TAG, "Releasing camera " + nCam);
            if (previewFlag) {    // if we have been getting previews from this camera
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            }
            // It is important to release the camera when it is not in us because it will drain the battery?
            mCamera.release();
            mCamera = null; // so we know it has been released
        } else Log.e(TAG, "No camera to release");
    }

    // what is preview size?
    private static void getPreviewSize (Camera mCamera, int nPixels) { //	pick one of the available preview size
        String TAG = "getPreviewSize";
        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> cSizes = params.getSupportedPictureSizes();
        int dPixels, dMinPixels = -1;
        if (DBG) Log.i(TAG, "Looking for about " + nPixels + " pixels");
        for (Camera.Size cSize : cSizes) {    // step through available camera preview image sizes
            if (DBG) Log.i(TAG, "Size " + cSize.height + " x " + cSize.width); // debug log output
//			use desired pixel count as a guide to selection
            dPixels = Math.abs(cSize.height * cSize.width - nPixels);
            if (dMinPixels < 0 || dPixels < dMinPixels) {
                mCameraHeight = cSize.height;
                mCameraWidth = cSize.width;
                dMinPixels = dPixels;
            }
        }
        if (DBG)
            Log.i(TAG, "Nearest fit available preview image size: " + mCameraHeight + " x " + mCameraWidth);
    }



//------- nested class DrawOnTop ---------------------------------------------------------------

    class DrawOnTop extends View {
        Bitmap mBitmap;
        byte[] mYUVData;
        int[] mRGBData;
        int[][] mNewData;
        int[][] mLastData;
        float[][] mEx;
        float[][] mEy;
        float[][] mEt;
        float[][] mU;
        float[][] mV;
        int mImageWidth, mImageHeight;
        int[] mRedHistogram;
        int[] mGreenHistogram;
        int[] mBlueHistogram;
        int[] mBrightnessHistogram;
        Paint mPaintBlack;
        Paint mPaintYellow;
        Paint mPaintRed;
        Paint mPaintGreen;
        Paint mPaintBlue;
        int mTextsize = 90;        // controls size of text on screen
        int mLeading;              // spacing between text lines
        RectF barRect = new RectF();    // used in drawing histogram
        double redMean, greenMean, blueMean, brightMean;    // computed results
        double redStdDev, greenStdDev, blueStdDev, brightStdDev;
        String TAG = "DrawOnTop";       // for logcat output

        public DrawOnTop(Context context) { // constructor
            super(context);

            mPaintBlack = makePaint(Color.BLACK);
            mPaintYellow = makePaint(Color.YELLOW);
            mPaintRed = makePaint(Color.RED);
            mPaintGreen = makePaint(Color.GREEN);
            mPaintBlue = makePaint(Color.BLUE);

            mBitmap = null;    // will be set up later in Preview - PreviewCallback
            mYUVData = null;
            mRGBData = null;
            mNewData = null;
            mLastData = null;
            mEx = null;
            mEy = null;
            mEt = null;
            mU = null;
            mV = null;
            mRedHistogram = new int[256];
            mGreenHistogram = new int[256];
            mBlueHistogram = new int[256];
            mBrightnessHistogram = new int[256];
            barRect = new RectF();    // moved here to reduce GC
            if (DBG) Log.i(TAG, "DrawOnTop textsize " + mTextsize);
            mLeading = mTextsize * 6 / 5;    // adjust line spacing
            if (DBG) Log.i(TAG, "DrawOnTop Leading " + mLeading);

        }

        Paint makePaint(int color) {
            Paint mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(color);
            mPaint.setTextSize(mTextsize);
            mPaint.setTypeface(Typeface.MONOSPACE);
            return mPaint;
        }

        // Called when preview is drawn on screen
        // Compute some statistics and draw text and histograms on screen
        @Override
        protected void onDraw (Canvas canvas) {
            String TAG = "onDraw";
            if (mBitmap == null) {    // sanity check for the existence of a BitMap
                Log.w(TAG, "mBitMap is null");
                super.onDraw(canvas);
                return;    // because not yet set up
            }

            // Convert image from YUV to RGB format (presumably a matrix):
            // What is YUV format??
            // now instead of this function we want to replace
            // decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);
            // with
            decodeYUV420SPGrayscale(mRGBData, mYUVData, mImageWidth, mImageHeight);
            // Now do some image processing here:

            //Calculate brightness grdients and velocity
            calculateBrightnessGradients();
            //For now we know V=0
            calculateU();

            // Calculate histograms
            calculateIntensityHistograms(mRGBData, mRedHistogram, mGreenHistogram, mBlueHistogram, mBrightnessHistogram, mImageWidth, mImageHeight);

            // calculate means and standard deviations
            calculateMeanAndStDev(mRedHistogram, mGreenHistogram, mBlueHistogram, mBrightnessHistogram, nPixels);

            // Finally, use the results to draw things on top of screen:
            int canvasHeight = canvas.getHeight();
            int canvasWidth = canvas.getWidth();
            int newImageWidth = canvasWidth - 200;
            int marginWidth = (canvasWidth - newImageWidth) / 2;

            // Draw mean (truncate to integer) text on screen
            String imageMeanStr = "Mean    (brightness): " + String.format("%4d", (int) brightMean);
                    //+ ", " +
                    //String.format("%4d", (int) greenMean) + ", " + String.format("%4d", (int) blueMean);
            drawTextOnBlack(canvas, imageMeanStr, marginWidth + 10, mLeading, mPaintYellow);
            // Draw standard deviation (truncate to integer) text on screen
            String imageStdDevStr = "Std Dev (brightness): " + String.format("%4d", (int) brightStdDev);
                    // + ", " +
                    // String.format("%4d", (int) greenStdDev) + ", " + String.format("%4d", (int) blueStdDev);
            drawTextOnBlack(canvas, imageStdDevStr, marginWidth + 10, 2 * mLeading, mPaintYellow);

            float barWidth = ((float) newImageWidth) / 256;
            // Draw red intensity histogram
            // drawHistogram(canvas, mPaintRed, mRedHistogram, nPixels, canvasHeight - 2 * 100, marginWidth, barWidth);
            // Draw green intensity histogram
            // drawHistogram(canvas, mPaintGreen, mGreenHistogram, nPixels, canvasHeight - 100, marginWidth, barWidth);
            // Draw blue intensity histogram
            // drawHistogram(canvas, mPaintBlue, mBlueHistogram, nPixels, canvasHeight, marginWidth, barWidth);
            drawHistogram(canvas, mPaintBlack, mBrightnessHistogram, nPixels, canvasHeight, marginWidth, barWidth);

            super.onDraw(canvas);

        } // end onDraw method

        public void calculateBrightnessGradients() {
            if (mLastData == null) {
                return;
            }
            float epx = getResources().getDisplayMetrics().xdpi;
            for (int i = 0; i < mLastData.length; i++) {
                for (int j = 0; j < mLastData[i].length; j++) {
                    mEx[i][j] = 1/epx*(1/4*(mLastData[i][j+1] + mLastData[i+1][j+1] + mNewData[i][j+1] + mNewData[i+1][j+1]) - 1/4*(mLastData[i][j] + mLastData[i+1][j] + mNewData[i][j] + mNewData[i+1][j]));
                    mEy[i][j] = 1/epx*(1/4*(mLastData[i+1][j] + mLastData[i+1][j+1] + mNewData[i+1][j] + mNewData[i+1][j+1]) - 1/4*(mLastData[i][j] + mLastData[i][j+1] + mNewData[i][j] + mNewData[i][j+1]));
                    mEt[i][j] = 1/epx*(1/4*(mNewData[i][j] + mNewData[i][j+1] + mNewData[i+1][j] + mNewData[i+1][j+1]) - 1/4*(mLastData[i][j] + mLastData[i][j+1] + mLastData[i+1][j] + mLastData[i+1][j+1]));
                }
            }
        }

        public void calculateU() {
            if (mLastData == null) {
                return;
            }
            for (int i = 0; i < mLastData.length; i++) {
                for (int j = 0; j < mLastData[i].length; j++) {
                    mU[i][j] = -1*mEt[i][j]/mEx[i][j];
                }
            }
        }
        
        // here is where we can make some alterations by somehow just extracting the "Y" portion of the YUV matrix?
        // not using this anymore
//        public void decodeYUV420SP (int[] rgb, byte[] yuv420sp, int width, int height) { // convert image in YUV420SP format to RGB format
//            final int frameSize = width * height;
//
//            for (int j = 0, pix = 0; j < height; j++) {
//                int uvp = frameSize + (j >> 1) * width;    // index to start of u and v data for this row
//                int u = 0, v = 0;
//                for (int i = 0; i < width; i++, pix++) {
//                    int y = (0xFF & ((int) yuv420sp[pix])) - 16;
//                    if (y < 0) y = 0;
//                    if ((i & 1) == 0) { // even row & column (u & v are at quarter resolution of y)
//                        v = (0xFF & yuv420sp[uvp++]) - 128;
//                        u = (0xFF & yuv420sp[uvp++]) - 128;
//                    }
//
//                    int y1192 = 1192 * y;
//                    int r = (y1192 + 1634 * v);
//                    int g = (y1192 - 833 * v - 400 * u);
//                    int b = (y1192 + 2066 * u);
//
//                    if (r < 0) r = 0;
//                    else if (r > 0x3FFFF) r = 0x3FFFF;
//                    if (g < 0) g = 0;
//                    else if (g > 0x3FFFF) g = 0x3FFFF;
//                    if (b < 0) b = 0;
//                    else if (b > 0x3FFFF) b = 0x3FFFF;
//
//                    rgb[pix] = 0xFF000000 | ((r << 6) & 0xFF0000) | ((g >> 2) & 0xFF00) | ((b >> 10) & 0xFF);
//                }
//            }
//        }

        // Let's put a function here that converts the rgb pixel matrix into a greyscale intensity matrix
        // Or let's do a function that can extract the Y values of the YUV420SP object
        // does this already do this? How do I check the output??
        public void decodeYUV420SPGrayscale (int[] rgb, byte[] yuv420sp, int width, int height) { // extract grey RGB format image
            final int frameSize = width * height;

            // This is much simpler since we can ignore the u and v components
            for (int pix = 0; pix < frameSize; pix++) {
                int y = (0xFF & ((int) yuv420sp[pix])) - 16;
                if (y < 0) y = 0;
                if (y > 0xFF) y = 0xFF;
                rgb[pix] = 0xFF000000 | (y << 16) | (y << 8) | y;
            }
        }

        // This is where we finally actually do some "image processing"!
        // Now we need to adjust this to

        public void calculateIntensityHistograms (int[] rgb, int[] redHistogram, int[] greenHistogram, int[] blueHistogram, int[] brightHistogram, int width, int height) {
            final int dpix = 1;
            int red, green, blue, bright, bin, pixVal;
            for (bin = 0; bin < 256; bin++) { // reset the histograms
                redHistogram[bin] = 0;
                greenHistogram[bin] = 0;
                blueHistogram[bin] = 0;
                brightHistogram[bin] = 0;
            }
            for (int pix = 0; pix < width * height; pix += dpix) {
                pixVal = rgb[pix];
                bright = pixVal & 0xFF;
                brightHistogram[bright]++;
                pixVal = rgb[pix];
                blue = pixVal & 0xFF;
                blueHistogram[blue]++;
                pixVal = pixVal >> 8;
                green = pixVal & 0xFF;
                greenHistogram[green]++;
                pixVal = pixVal >> 8;
                red = pixVal & 0xFF;
                redHistogram[red]++;
            }
        }

        private void calculateMeanAndStDev (int mRedHistogram[], int mGreenHistogram[], int mBlueHistogram[], int mBrightnessHistogram[], int nPixels) {
            // Calculate first and second moments (zeroth moment equals nPixels)
            double red1stMoment = 0, green1stMoment = 0, blue1stMoment = 0, bright1stMoment = 0;
            double red2ndMoment = 0, green2ndMoment = 0, blue2ndMoment = 0, bright2ndMoment = 0;
            double binsquared = 0;
            for (int bin = 0; bin < 256; bin++) {
                binsquared += (bin << 1) - 1;    // n^2 - (n-1)^2 = 2*n - 1
                red1stMoment += mRedHistogram[bin] * bin;
                green1stMoment += mGreenHistogram[bin] * bin;
                blue1stMoment += mBlueHistogram[bin] * bin;
                red2ndMoment += mRedHistogram[bin] * binsquared;
                green2ndMoment += mGreenHistogram[bin] * binsquared;
                blue2ndMoment += mBlueHistogram[bin] * binsquared;
                bright1stMoment += mBrightnessHistogram[bin] * binsquared;
                bright2ndMoment += mBrightnessHistogram[bin] * binsquared;

            } // bin

            redMean = red1stMoment / nPixels;
            greenMean = green1stMoment / nPixels;
            blueMean = blue1stMoment / nPixels;
            brightMean = bright1stMoment / nPixels;

            redStdDev = Math.sqrt(red2ndMoment / nPixels - redMean * redMean);
            greenStdDev = Math.sqrt(green2ndMoment / nPixels - greenMean * greenMean);
            blueStdDev = Math.sqrt(blue2ndMoment / nPixels - blueMean * blueMean);
            brightStdDev = Math.sqrt(bright2ndMoment / nPixels - brightMean * brightMean);
        }

        private void drawTextOnBlack (Canvas canvas, String str, int rPos, int cPos, Paint mPaint) { // make text stand out from background by providing thin black border
            canvas.drawText(str, rPos - 1, cPos - 1, mPaintBlack);
            canvas.drawText(str, rPos + 1, cPos - 1, mPaintBlack);
            canvas.drawText(str, rPos + 1, cPos + 1, mPaintBlack);
            canvas.drawText(str, rPos - 1, cPos + 1, mPaintBlack);
            canvas.drawText(str, rPos, cPos, mPaint);
        }

        private void drawHistogram (Canvas canvas, Paint mPaint,
                                    int mHistogram[], int nPixels,
                                    int mBottom, int marginWidth, float barWidth) {
            float barMaxHeight = 3000; // controls vertical scale of histogram
            float barMarginHeight = 2;

            barRect.bottom = mBottom;
            barRect.left = marginWidth;
            barRect.right = barRect.left + barWidth;
            for (int bin = 0; bin < 256; bin++) {
                float prob = (float) mHistogram[bin] / (float) nPixels;
                barRect.top = barRect.bottom - Math.min(80, prob * barMaxHeight) - barMarginHeight;
                canvas.drawRect(barRect, mPaintBlack);
                barRect.top += barMarginHeight;
                canvas.drawRect(barRect, mPaint);
                barRect.left += barWidth;
                barRect.right += barWidth;
            }
        }
    }


// -------- nested class Preview --------------------------------------------------------------

    class Preview extends SurfaceView implements SurfaceHolder.Callback {    // deal with preview that will be shown on screen
        SurfaceHolder mHolder;
        DrawOnTop mDrawOnTop;
        boolean mFinished;
        String TAG = "PreView";    // tag for LogCat

        public Preview (Context context, DrawOnTop drawOnTop) { // constructor
            super(context);

            mDrawOnTop = drawOnTop;
            mFinished = false;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            //  Following is deprecated setting, but required on Android versions prior to 3.0:
            //  mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated (SurfaceHolder holder) {
            String TAG = "surfaceCreated";
            PreviewCallback mPreviewCallback;
            if (mCamera == null) {    // sanity check
                Log.e(TAG, "ERROR: camera not open");
                System.exit(0);
            }
            Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(mCam, info);
            // show some potentially useful information in log file
            switch (info.facing) {    // see which camera we are using
                case Camera.CameraInfo.CAMERA_FACING_BACK:
                    Log.i(TAG, "Camera " + mCam + " facing back");
                    break;
                case Camera.CameraInfo.CAMERA_FACING_FRONT:
                    Log.i(TAG, "Camera " + mCam + " facing front");
                    break;
            }
            if (DBG) Log.i(TAG, "Camera " + mCam + " orientation " + info.orientation);

            mPreviewCallback = new PreviewCallback() {
                public void onPreviewFrame(byte[] data, Camera camera) { // callback
                    String TAG = "onPreviewFrame";
                    if ((mDrawOnTop == null) || mFinished) return;
                    if (mDrawOnTop.mBitmap == null)  // need to initialize the drawOnTop companion?
                        setupArrays(data, camera);
                    // Pass YUV image data to draw-on-top companion
                    System.arraycopy(data, 0, mDrawOnTop.mYUVData, 0, data.length);
                    mDrawOnTop.invalidate();
                }
            };

            try {
                mCamera.setPreviewDisplay(holder);
                // Preview callback will be used whenever new viewfinder frame is available
                mCamera.setPreviewCallback(mPreviewCallback);
            } catch (IOException e) {
                Log.e(TAG, "ERROR: surfaceCreated - IOException " + e);
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceDestroyed (SurfaceHolder holder) {
            String TAG = "surfaceDestroyed";
            // Surface will be destroyed when we return, so stop the preview.
            mFinished = true;
            if (mCamera != null) {    // not expected
                Log.e(TAG, "ERROR: camera still open");
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged (SurfaceHolder holder, int format, int w, int h) {
            String TAG = "surfaceChanged";
            //	Now that the size is known, set up the camera parameters and begin the preview.
            if (mCamera == null) {    // sanity check
                Log.e(TAG, "ERROR: camera not open");
                System.exit(0);
            }
            if (DBG) Log.v(TAG, "Given parameters h " + h + " w " + w);
            if (DBG) Log.v(TAG, "What we are asking for h " + mCameraHeight + " w " + mCameraWidth);
            if (h != mCameraHeight || w != mCameraWidth)
                Log.w(TAG, "Mismatch in image size " + " " + h + " x " + w + " vs " + mCameraHeight + " x " + mCameraWidth);
            // this will be sorted out with a setParamaters() on mCamera
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mCameraWidth, mCameraHeight);
            // check whether following is within PreviewFpsRange ?
            parameters.setPreviewFrameRate(15);    // deprecated
            // parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Log.e(TAG, "ERROR: setParameters exception " + e);
                System.exit(0);
            }
            mCamera.startPreview();
        }

        private void setupArrays (byte[] data, Camera camera) {
            String TAG = "setupArrays";
            if (DBG) Log.i(TAG, "Setting up arrays");
            Camera.Parameters params = camera.getParameters();
            mDrawOnTop.mImageHeight = params.getPreviewSize().height;
            mDrawOnTop.mImageWidth = params.getPreviewSize().width;
            if (DBG)
                Log.i(TAG, "height " + mDrawOnTop.mImageHeight + " width " + mDrawOnTop.mImageWidth);
            mDrawOnTop.mBitmap = Bitmap.createBitmap(mDrawOnTop.mImageWidth,
                    mDrawOnTop.mImageHeight, Bitmap.Config.RGB_565);
            mDrawOnTop.mRGBData = new int[mDrawOnTop.mImageWidth * mDrawOnTop.mImageHeight];
            mDrawOnTop.mLastData = mDrawOnTop.mNewData;
            mDrawOnTop.mNewData = new int[mDrawOnTop.mImageWidth][mDrawOnTop.mImageHeight];
            if (DBG)
                Log.i(TAG, "data length " + data.length); // should be width*height*3/2 for YUV format
            mDrawOnTop.mYUVData = new byte[data.length];
            int dataLengthExpected = mDrawOnTop.mImageWidth * mDrawOnTop.mImageHeight * 3 / 2;
            if (data.length != dataLengthExpected)
                Log.e(TAG, "ERROR: data length mismatch " + data.length + " vs " + dataLengthExpected);
        }

    }

//////////////////////////////////////////////////////////////////////////////////////////////////////

    // For Android 6.0 (API Level 25)  permission requests

    private static final int REQ_PERMISSION_THISAPP = 0; // unique code for permissions request
    private static boolean bUseCameraFlag = true;			   // we want to use the camera
    private static boolean bCameraPermissionGranted = false;   // have CAMERA permission

    private void getPermissions () {
        String TAG = "getPermissions";
        if (DBG) Log.v(TAG, "in getPermissions()");
        if (Build.VERSION.SDK_INT >= 23) {            // need to ask at runtime as of Android 6.0
            String sPermissions[] = new String[2];    // space for possible permission strings
            int nPermissions = 0;	// count of permissions to be asked for
            if (bUseCameraFlag) {    // protection level: dangerous
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    bCameraPermissionGranted = true;
                else sPermissions[nPermissions++] = Manifest.permission.CAMERA;
            }
            if (nPermissions > 0) {
                if (DBG) Log.d(TAG, "Need to ask for " + nPermissions + " permissions");
                if (nPermissions < sPermissions.length)
                    sPermissions = Arrays.copyOf(sPermissions, nPermissions);
                if (DBG) {
                    for (String sPermission : sPermissions) Log.w(TAG, sPermission);    // debugging only
                }
                requestPermissions(sPermissions, REQ_PERMISSION_THISAPP);    // start the process
            }
        } else {    // in earlier API, permission is dealt with at install time, not run time
            if (bUseCameraFlag) bCameraPermissionGranted = true;
        }
    }

    //	Note: onRequestPermissionsResult happens *after* user has interacted with the permissions request
    //  So, annoyingly, have to now (re-)do things that didn't happen in onCreate() because permissions were not there yet.

    @Override
    // overrides method in android.app.Activity
    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        String TAG = "onRequestPermitResult";
        if (DBG) Log.w(TAG, "in onRequestPermissionsResult(...) (" + requestCode + ")");
        if (requestCode != REQ_PERMISSION_THISAPP) {    // check that this is a response to our request
            Log.e(TAG, "Unexpected requestCode " + requestCode);    // can this happen?
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        int n = grantResults.length;
        if (DBG) Log.w(TAG, "requestCode=" + requestCode + " for " + n + " permissions");
        for (int i = 0; i < n; i++) {
            if (DBG) Log.w(TAG, "permission " + permissions[i] + " " + grantResults[i]);
            switch (permissions[i]) {
                case Manifest.permission.CAMERA:
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        if (DBG) Log.w(TAG, "CAMERA Permission granted (" + i + ")");
                        bCameraPermissionGranted = true;
                        // redo the setup in onResume(...) ?
                    } else {
                        bUseCameraFlag = false;
                        String str = "You must grant CAMERA permission to use the camera!";
                        Log.e(TAG, str);
                        makeToast(str, 1);
                    }
                    break;
            }
        }
    }

    private void makeToast (CharSequence message, int nLength) {
        // Toast.LENGTH_SHORT == 0, Toast.LENGTH_LONG == 1
        Toast toast = Toast.makeText(this, message, nLength);
        toast.show();
    }

}

////////////////////////////////////////////////////////////////////////////////////

// NOTE: the "Camera" class is deprecated as of API 21, but very few
// devices support the new Camera2 API, and even fewer support it fully
// and correctly (as of summer 2015: Motorola Nexus 5 & 6 and just possibly Samsung S6)
// So, for now, we use the "old" Camera class here.

////////////////////////////////////////////////////////////////////////////////////

// TODO: remove before release

///////////////////////////////////////////////////////////////////////////////////