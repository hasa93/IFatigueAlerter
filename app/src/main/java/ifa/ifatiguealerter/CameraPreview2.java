package ifa.ifatiguealerter;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview2 extends SurfaceView {
	private SurfaceHolder mHolder;
	private Camera mCamera;

	public CameraPreview2(Context context, Camera camera) {
		super(context);
		mCamera = camera;
		mHolder = getHolder();
//		mHolder.addCallback(this);
	}

	public void refreshCamera(Camera camera) {
		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}
		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e) {
			// ignore: tried to stop a non-existent preview
		}

		setCamera(camera);
		try {
//			mCamera.setPreviewDisplay(mHolder);
			mCamera.startPreview();
		} catch (Exception e) {
			Log.d(VIEW_LOG_TAG, "Error starting camera preview: " + e.getMessage());
		}
	}

	public void setCamera(Camera camera) {
		//method to set a camera instance
		Log.i(BlinkCounter.TAG, "camset");
		mCamera = camera;
	}

	public void closeCam() throws IOException {
		mCamera.setPreviewDisplay(null);
		mCamera.stopPreview();
		mCamera.release();
	}


}