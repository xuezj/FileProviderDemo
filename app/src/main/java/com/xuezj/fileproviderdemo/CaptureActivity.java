package com.xuezj.fileproviderdemo;


import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.xuezj.fileproviderdemo.camera.CameraManager;
import com.xuezj.fileproviderdemo.decoding.CaptureActivityHandler;
import com.xuezj.fileproviderdemo.decoding.InactivityTimer;
import com.xuezj.fileproviderdemo.decoding.RGBLuminanceSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

public class CaptureActivity extends Activity
        implements Callback {

    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private Button back;
    private ImageButton menu;
    private CheckBox collection;
    private TextView layoutTitle;
    private static final int PARSE_BARCODE_FAIL = 303;
    private ProgressDialog mProgress;
    private String photo_path;
    private Bitmap scanBitmap;
    private Dialog loadingDialog;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        CameraManager.MIN_FRAME_WIDTH = (int) getResources().getDimension(R.dimen.camera_frame_width);
        CameraManager.MIN_FRAME_HEIGHT = (int) getResources().getDimension(R.dimen.camera_frame_width);
        CameraManager.MAX_FRAME_WIDTH = (int) getResources().getDimension(R.dimen.camera_frame_width);
        CameraManager.MAX_FRAME_HEIGHT = (int) getResources().getDimension(R.dimen.camera_frame_width);
        CameraManager.init(getApplication());

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        back = (Button) findViewById(R.id.back);

        back.setOnClickListener(buttonlistener);
        // txtResult = (TextView) findViewById(R.id.txtResult);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);

    }

    private Button.OnClickListener buttonlistener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.back:
                    finish();
                    break;
                default:
                    break;
            }

        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(CaptureActivity.this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            return;
        } catch (RuntimeException e) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    public void handleDecode(Result obj, Bitmap barcode) {
        inactivityTimer.onActivity();
        viewfinderView.drawResultBitmap(barcode);
        //playBeepSoundAndVibrate();
        /*
		 * txtResult.setText(obj.getBarcodeFormat().toString() + ":" +
		 * obj.getText());
		 */
        dealwithmessage(obj);

    }

    private void dealwithmessage(Result obj) {
        String Barcode = obj.getBarcodeFormat().toString();
        String Text = obj.getText().toString();
        Log.d("CaptureActivity", "成功："+Text);
        finish();


    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(
                    R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            photo_path = data.getStringExtra("RESULT");

            System.out.println("-----" + photo_path);
            //if(requestCode== REQUEST_CODE){
            //获取选中图片的路径
//				 Uri uri = data.getData();
//				          Cursor cursor = this.getContentResolver().query(uri, null, null, null, null);
//				          cursor.moveToFirst();
//				           for (int i = 0; i < cursor.getColumnCount(); i++)
//				            {// 取得图片uri的列名和此列的详细信息
//				                System.out.println(i + "-" + cursor.getColumnName(i) + "-" + cursor.getString(i));
//				           }
//
//				System.out.println("-----"+data);

//				Cursor cursor = this.getContentResolver().query(u, null, null, null, null);
//				if (cursor.moveToFirst()) {
//
//					photo_path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
//
//				}


            //cursor.close();

            mProgress = new ProgressDialog(CaptureActivity.this);
            mProgress.setMessage("正在扫描...");
            mProgress.setCancelable(false);
            mProgress.show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Result result = scanningImage(photo_path);
                    if (result != null) {
                        dealwithmessage(result);
                        mProgress.dismiss();
                        CaptureActivity.this.finish();
                    } else {
                        mProgress.dismiss();
                        Message m = mHandler.obtainMessage();
                        m.what = PARSE_BARCODE_FAIL;
                        mHandler.sendMessage(m);
                    }
                }
            }).start();
            //}
        }
    }

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            mProgress.dismiss();
            switch (msg.what) {
                case PARSE_BARCODE_FAIL:
                    Toast.makeText(CaptureActivity.this, "无法识别", Toast.LENGTH_LONG).show();
                    break;
            }
        }

    };
    /**
     * 扫描二维码图片的方法
     *
     * @param path
     * @return
     */
    MultiFormatReader multiFormatReader;

    public Result scanningImage(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        multiFormatReader = new MultiFormatReader();

        //BufferedImage image =null;
        Hashtable<DecodeHintType, String> hints = new Hashtable<DecodeHintType, String>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); //设置二维码内容的编码

        multiFormatReader.setHints(hints);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 先获取原大小
        scanBitmap = BitmapFactory.decodeFile(path, options);
        options.inJustDecodeBounds = false; // 获取新的大小
        int sampleSize = (int) (options.outHeight / (float) 200);
        if (sampleSize <= 0)
            sampleSize = 1;
        //options.inSampleSize = sampleSize;
        scanBitmap = BitmapFactory.decodeFile(path, options);
        RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        int width = scanBitmap.getWidth();
        int height = scanBitmap.getHeight();
//		PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(Bitmap2Bytes(scanBitmap), width, height, height, height, height, height);
//	    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            return multiFormatReader.decodeWithState(bitmap1);
        } catch (ReaderException re) {
            // continue
        } finally {
            multiFormatReader.reset();
        }
//		QRCodeReader reader = new QRCodeReader();
//		try {
//			//return new MultiFormatReader().decode(bitmap1,hints);
//			return reader.decode(bitmap1, hints);
//
//		} catch (NotFoundException e) {
//			e.printStackTrace();
//		} catch (ChecksumException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (FormatException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
        return null;
    }

    public byte[] Bitmap2Bytes(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }


}
