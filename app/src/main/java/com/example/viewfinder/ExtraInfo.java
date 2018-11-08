// Optional code for showing information about camera and camera parameters in log file

package com.example.viewfinder;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

public class ExtraInfo
{	//	methods for dumping potentially useful information in the log
    private static boolean DBG=false;
    private static boolean bShowFlattenFlag=true;	// show parameters.flatten()
    private static boolean bShowDumpFlag=false;	    // show parameters.dump()

	public static void showDisplayInfo (Context context) // need context
	{	// put info about display into log
		String TAG = "showDisplayInfo";
//		get display screen dpi and rows and columns:
//		DisplayMetrics mDisplayMetrics = new DisplayMetrics();
//		getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
		DisplayMetrics mDisplayMetrics = context.getResources().getDisplayMetrics();
		int screenWidth = mDisplayMetrics.widthPixels; 
		int screenHeight = mDisplayMetrics.heightPixels; 
		float screenXdpi = mDisplayMetrics.xdpi;
		float screenYdpi = mDisplayMetrics.ydpi;
		Log.i(TAG, "Display screen " + screenHeight + " x " + screenWidth);
		Log.i(TAG, "Display screen " + (int) screenXdpi + " X dpi " + (int) screenYdpi + " Y dpi");
	}

	public static void showPreviewSizes (Camera mCamera)
	{	//	list the available preview image sizes for this camera in the log
		String TAG="showPreviewSizes";
		Camera.Parameters params = mCamera.getParameters();
		List<Camera.Size> cSizes = params.getSupportedPictureSizes();
		for (Camera.Size cSize : cSizes) {    // step through available camera image sizes
			Log.i(TAG, "Size " + cSize.height + " x " + cSize.width); // debug log output
		}
	}

	public static void showCameraInfo (Camera mCamera)
	{	// show camera info in log, for specific camera
		Camera.Parameters params = mCamera.getParameters();
		String TAG = "showCameraInfo";
		if (params.isVideoStabilizationSupported()) {    // API 15
			boolean videoStabilization = params.getVideoStabilization();
			Log.i(TAG, "videoStabilization " + videoStabilization);
		}
		else Log.i(TAG, "videoStabilization not supported");
		if (params.isAutoExposureLockSupported()) {    // API 14
			boolean autoExposureLock = params.getAutoExposureLock();
			Log.i(TAG, "autoExposureLock " + autoExposureLock);
		}
		else Log.i(TAG, "autoExpsoreLock not supported");
		if (params.isAutoWhiteBalanceLockSupported()) {    // API 14
			boolean autoWhiteBalanceLock = params.getAutoWhiteBalanceLock();
			Log.i(TAG, "autoWhiteBalanceLock " + autoWhiteBalanceLock);
		}
		else Log.i(TAG, "autoWhiteBalanceLock not supported");
		if (params.isZoomSupported()) {
			int zoom = params.getZoom();
			Log.i(TAG, "zoom " + zoom);
		}
		else Log.i(TAG, "zoom not supported");
		String whiteBalance = params.getWhiteBalance();
		Log.i(TAG, "whiteBalance " + whiteBalance);
		int exposureCompensation = params.getExposureCompensation();
		Log.i(TAG, "exposureCompensation " + exposureCompensation);
		String antiBanding = params.getAntibanding();
		if (antiBanding != null) Log.i(TAG, "antiBanding " + antiBanding);
		String colorEffect = params.getColorEffect();
		Log.i(TAG, "colorEffect " + colorEffect);
		String flashMode = params.getFlashMode();
		if (flashMode != null) Log.i(TAG, "flashMode " + flashMode);
		float focalLength = params.getFocalLength();
		Log.i(TAG, "focalLength " + focalLength + " mm");
		float[] focalDistances = new float[3];
		params.getFocusDistances(focalDistances);
		// consider FOCUS_MODE_CONTINUOUS_VIDEO
		// consider calling autoFocus(AutoFocusCallBack), cancelAutoFocus(), startPreview()
		Log.i(TAG, "focal distances " + focalDistances[0] + " " + focalDistances[1] + " " + focalDistances[2]);
		String focusMode = params.getFocusMode();
		// call autoFocus(AutoFocusCallback) to start the focus if mode is _AUTO
		Log.i(TAG, "focusMode " + focusMode);
		float horizontalViewAngle = params.getHorizontalViewAngle();
		float verticalViewAngle = params.getVerticalViewAngle();
		Log.i(TAG, "FOV " + horizontalViewAngle + " x " + verticalViewAngle + " degrees");
		int pictureFormat = params.getPictureFormat();    // JPEG is 256
		Log.i(TAG, "pictureFormat " + pictureFormatString(pictureFormat));    // ImageFormat
		Camera.Size pictureSize = params.getPictureSize();
		Log.i(TAG, "pictureSize " + pictureSize.height + " x " + pictureSize.width);
		int[] range = new int[2];
		params.getPreviewFpsRange(range);
		Log.i(TAG, "PreviewFpsRange " + range[0] / 1000.0 + " " + range[1] / 1000.0);
		int frameRate = params.getPreviewFrameRate();    // deprecated
		Log.i(TAG, "previewFrameRate " + frameRate);
		Camera.Size previewSize = params.getPreviewSize();
		Log.i(TAG, "previewSize " + previewSize.height + " x " + previewSize.width);
		String sceneMode = params.getSceneMode();
		Log.i(TAG, "sceneMode " + sceneMode);    // "SCENE_MODE_AUTO" means "OFF"
	}

	public static void showCameraInfoAll ()
	{	// show info for all cameras
		String TAG="showCameraInfoAll";
		int numCam = Camera.getNumberOfCameras();	// number of cameras
		Log.i(TAG, "Number of cameras " + numCam);
		Camera.CameraInfo cameraInfo = new android.hardware.Camera.CameraInfo();
		for (int k=0; k < numCam; k++) {
			String str;
			Camera.getCameraInfo(k, cameraInfo);	// get info for Camera k
			switch (cameraInfo.facing) {	// see which camera that is
				case Camera.CameraInfo.CAMERA_FACING_BACK:
					str = "Camera " + k + " facing back ";
					break;
				case Camera.CameraInfo.CAMERA_FACING_FRONT: 
					str = "Camera " + k + " facing front ";
					break;
				default:
					str = "Unknown camera " + k + " " + cameraInfo.facing + " ";
					break;
			}
			Log.i(TAG, str);
			if (MainActivity.mCamera != null) {	// sanity check
				Log.e(TAG, "ERROR: camera already open");
				return;
			}
			MainActivity.openCamera(k);
			showCameraInfo(MainActivity.mCamera);	// go show some details
            if (bShowFlattenFlag)
                showCameraFlatten(MainActivity.mCamera); // full set of parameters
            if (bShowDumpFlag)
                showCameraDump(MainActivity.mCamera); // full set of parameters
			MainActivity.releaseCamera(k, false);
		}
	}

	public static String pictureFormatString (int iform)
	{	// convert to human readable form
		switch(iform) {
			case ImageFormat.JPEG: return "JPEG";
			case ImageFormat.NV16: return "NV16"; 
			case ImageFormat.NV21: return "NV21";	// default for Camera preview
			case ImageFormat.RGB_565: return "RGB_565"; 
			case ImageFormat.YUY2: return "YUY2"; 
			case ImageFormat.YV12: return "YV12"; 
			case ImageFormat.YUV_420_888: return "YUV_420_888";	// preferred for Camera2 preview
			case ImageFormat.RAW10: return "RAW10"; 	// requires API 21 ?
			case ImageFormat.RAW_SENSOR: return "RAW_SENSOR";  // requires API 21 ?
			case ImageFormat.UNKNOWN:
			default: return "UNKNOWN";
		}
	}

	public static void showCameraFlatten (Camera mCamera)
    {   // show full set of parameters for camera using "flatten" method in Camera.Parameters
		String TAG="showCameraFlatten";
        Camera.Parameters params = mCamera.getParameters();
		String paramsflat = params.flatten();
		String flats[] = paramsflat.split(";");
		Log.v(TAG, "size=" + flats.length);
		for (String flat : flats) {
			Log.v(TAG, flat);
		}
	}

    public static void showCameraDump (Camera mCamera)
    { // show full set of parameters for camera using "dump" method in Camera.Parameters
        String TAG="showCameraDump";
        Camera.Parameters params = mCamera.getParameters();
		// the dump method is not directly accessible (and deprecated)
        try {   // use java reflection to get at hidden "dump" method 
			Method mDump = params.getClass().getDeclaredMethod("dump");
            if (DBG) Log.i(TAG, "Got method " + mDump);
			mDump.invoke(params);    // NOTE: dump doesn't return a result
            if (DBG) Log.i(TAG, "Invoked method " + mDump);
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
	}

}

// NOTE:  flatten and dump appear to produce the same information.

// NOTE: the "Camera" class is deprecated as of API 21, but very few
// devices support the new Camera2 API, and even fewer support it fully
// and correctly (as of summer 2015: Motorola Nexus 5 & 6 and just possibly Samsung S6)
// So, for now, we use the "old" Camera class here.

