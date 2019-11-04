package org.reactnative.camera.tasks;

import android.graphics.Rect;
import android.util.SparseArray;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.vision.barcode.Barcode;

import org.reactnative.barcodedetector.BarcodeFormatUtils;
import org.reactnative.camera.utils.ImageDimensions;
import org.reactnative.frame.RNFrame;
import org.reactnative.frame.RNFrameFactory;
import org.reactnative.barcodedetector.RNBarcodeDetector;

public class BarcodeDetectorAsyncTask extends android.os.AsyncTask<Void, Void, SparseArray<Barcode>> {

  private byte[] mImageData;
  private int mWidth;
  private int mHeight;
  private int mCropWidth;
  private int mCropHeight;
  private int mCropX;
  private int mCropY;
  private int mRotation;
  private RNBarcodeDetector mBarcodeDetector;
  private BarcodeDetectorAsyncTaskDelegate mDelegate;
  private double mScaleX;
  private double mScaleY;
  private ImageDimensions mImageDimensions;
  private int mPaddingLeft;
  private int mPaddingTop;

  public BarcodeDetectorAsyncTask(
      BarcodeDetectorAsyncTaskDelegate delegate,
      RNBarcodeDetector barcodeDetector,
      byte[] imageData,
      int width,
      int height,
      int rotation,
      float density,
      int facing,
      int viewWidth,
      int viewHeight,
      int viewPaddingLeft,
      int viewPaddingTop,
      int cropWidth,
      int cropHeight,
      int cropX,
      int cropY
  ) {
    mImageData = imageData;
    mWidth = width;
    mHeight = height;
    mCropWidth = cropWidth;
    mCropHeight = cropHeight;
    mCropX = cropX;
    mCropY = cropY;
    mRotation = rotation;
    mDelegate = delegate;
    mBarcodeDetector = barcodeDetector;
    mImageDimensions = new ImageDimensions(width, height, rotation, facing);
    mScaleX = (double) (viewWidth) / (mImageDimensions.getWidth() * density);
    mScaleY = (double) (viewHeight) / (mImageDimensions.getHeight() * density);
    mPaddingLeft = viewPaddingLeft;
    mPaddingTop = viewPaddingTop;
  }

  @Override
  protected SparseArray<Barcode> doInBackground(Void... ignored) {
    if (isCancelled() || mDelegate == null || mBarcodeDetector == null || !mBarcodeDetector.isOperational()) {
      return null;
    }

    RNFrame frame = RNFrameFactory.buildFrame(mImageData, mCropWidth, mCropHeight, mRotation);
    return mBarcodeDetector.detect(frame);
  }

  @Override
  protected void onPostExecute(SparseArray<Barcode> barcodes) {
    super.onPostExecute(barcodes);

    if (barcodes == null) {
      mDelegate.onBarcodeDetectionError(mBarcodeDetector);
    } else {
      if (barcodes.size() > 0) {
        mDelegate.onBarcodesDetected(serializeEventData(barcodes));
      }
      mDelegate.onBarcodeDetectingTaskCompleted();
    }
  }

  private WritableArray serializeEventData(SparseArray<Barcode> barcodes) {
    WritableArray barcodesList = Arguments.createArray();

    for (int i = 0; i < barcodes.size(); i++) {
      Barcode barcode = barcodes.valueAt(i);
      WritableMap serializedBarcode = Arguments.createMap();

      serializedBarcode.putString("data", barcode.displayValue);
      serializedBarcode.putString("rawData", barcode.rawValue);
      serializedBarcode.putString("type", BarcodeFormatUtils.get(barcode.format));
      serializedBarcode.putMap("bounds", processBounds(barcode.getBoundingBox()));
      barcodesList.pushMap(serializedBarcode);
    }

    return barcodesList;
  }

  private WritableMap processBounds(Rect frame) {
    int cropX = mCropX;
    int cropY = mCropY;
    // Crop x/y are relative to landscape mode, we have to invert them for portrait mode
    if(mRotation == 90 || mRotation == -90) {
      cropX = mCropY;
      cropY = mCropX;
    }

    WritableMap origin = Arguments.createMap();
    int x = frame.left + cropX;
    int y = frame.top + cropY;

    if (frame.left < mWidth / 2) {
      x = x + mPaddingLeft / 2;
    } else if (frame.left > mWidth /2) {
      x = x - mPaddingLeft / 2;
    }

    if (frame.top < mHeight / 2) {
      y = y + mPaddingTop / 2;
    } else if (frame.top > mHeight / 2) {
      y = y - mPaddingTop / 2;
    }

    origin.putDouble("x", x * mScaleX);
    origin.putDouble("y", y * mScaleY);

    WritableMap size = Arguments.createMap();
    size.putDouble("width", frame.width() * mScaleX);
    size.putDouble("height", frame.height() * mScaleY);

    WritableMap bounds = Arguments.createMap();
    bounds.putMap("origin", origin);
    bounds.putMap("size", size);
    return bounds;
  }
}
